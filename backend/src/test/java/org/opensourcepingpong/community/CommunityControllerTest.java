package org.opensourcepingpong.community;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class CommunityControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final UUID user = UUID.randomUUID();
  private final AuthPrincipal principal = new AuthPrincipal(user, "user");
  private CommunityController controller;

  @BeforeEach
  void setUp() {
    controller = new CommunityController(jdbc, authorization);
  }

  @Test
  void announcementCreationRequiresAnnouncementManagePermission() {
    when(jdbc.update(startsWith("insert into community_posts"), any(Object[].class))).thenReturn(1);

    controller.announcement(principal, create(null));

    verify(authorization).require(user, Permission.ANNOUNCEMENT_MANAGE);
  }

  @Test
  void anonymousAnnouncementListOnlyAsksForPublicVisibility() {
    when(jdbc.queryForList(contains("p.visibility='PUBLIC'"), eq("ANNOUNCEMENT"), eq(false))).thenReturn(List.of());

    controller.announcements(null);

    verify(jdbc).queryForList(contains("p.visibility='PUBLIC'"), eq("ANNOUNCEMENT"), eq(false));
  }

  @Test
  void activeMemberAnnouncementListCanSeeMembersOnly() {
    when(jdbc.queryForList(startsWith("select id from memberships"), eq(user))).thenReturn(List.of(Map.of("id", UUID.randomUUID())));
    when(jdbc.queryForList(contains("p.visibility='PUBLIC'"), eq("ANNOUNCEMENT"), eq(true))).thenReturn(List.of());

    controller.announcements(principal);

    verify(jdbc).queryForList(contains("p.visibility='PUBLIC'"), eq("ANNOUNCEMENT"), eq(true));
  }

  @Test
  void playPostCannotExceedCapacityOrJoinTwice() {
    UUID post = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select id,post_type,status"), eq(post))).thenReturn(List.of(playPost(post, 1)));
    when(jdbc.queryForObject(startsWith("select count(*)"), eq(Integer.class), eq(post))).thenReturn(1);
    assertEquals("PLAY_POST_FULL", assertThrows(BusinessException.class, () -> controller.joinPlayPost(principal, post)).code());

    when(jdbc.queryForObject(startsWith("select count(*)"), eq(Integer.class), eq(post))).thenReturn(0);
    when(jdbc.update(startsWith("insert into meetup_participants"), any(), eq(post), eq(user))).thenThrow(new DuplicateKeyException("duplicate"));
    assertEquals("PLAY_ALREADY_JOINED", assertThrows(BusinessException.class, () -> controller.joinPlayPost(principal, post)).code());
  }

  @Test
  void playPostCreationUsesExplicitSqlTimestampTypes() {
    Instant eventTime = Instant.parse("2026-10-18T06:00:00Z");
    Instant expiresAt = Instant.parse("2026-10-18T10:00:00Z");
    var request = new CommunityController.Create(
        "Friday practice", "Singles practice", "PUBLIC", expiresAt, 2,
        eventTime, "Gym table 1", "In-app contact", null, null, null, false);
    var parameters = org.mockito.ArgumentCaptor.forClass(Object[].class);
    when(jdbc.update(startsWith("insert into community_posts"), any(Object[].class))).thenReturn(1);

    var response = controller.playPost(principal, request);

    assertNotNull(response.data().get("id"));
    verify(jdbc).update(startsWith("insert into community_posts"), parameters.capture());
    assertEquals(Timestamp.from(expiresAt), parameters.getValue()[7]);
    assertEquals(Timestamp.from(eventTime), parameters.getValue()[9]);
  }

  @Test
  void leavingPlayPostReleasesCurrentUsersPlace() {
    UUID post = UUID.randomUUID();
    when(jdbc.update(startsWith("delete from meetup_participants"), eq(post), eq(user))).thenReturn(1);

    controller.leavePlayPost(principal, post);

    verify(jdbc).update(startsWith("delete from meetup_participants"), eq(post), eq(user));
  }

  @Test
  void onlyMarketPostOwnerCanMarkSold() {
    UUID post = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select author_user_id"), eq(post), eq("MARKETPLACE")))
        .thenReturn(List.of(Map.of("author_user_id", UUID.randomUUID())));

    assertEquals("MARKET_POST_NOT_OWNER", assertThrows(BusinessException.class, () -> controller.markSold(principal, post)).code());

    verify(jdbc, never()).update(startsWith("update community_posts set status='SOLD'"), any(UUID.class));
  }

  @Test
  void marketPostRequiresAcceptedRulesAndCompleteConditionAndDelivery() {
    var missingAcknowledgement = marketCreate(false, "Good", "Meet in person");
    assertEquals("MARKET_RULES_REQUIRED",
        assertThrows(BusinessException.class, () -> controller.market(principal, missingAcknowledgement)).code());
    var missingCondition = marketCreate(true, " ", "Meet in person");
    assertEquals("MARKET_DETAILS_REQUIRED",
        assertThrows(BusinessException.class, () -> controller.market(principal, missingCondition)).code());
    verify(jdbc, never()).update(startsWith("insert into community_posts"), any(Object[].class));
  }

  @Test
  void marketPostCreationPersistsConditionAndDelivery() {
    when(jdbc.update(startsWith("insert into community_posts"), any(Object[].class))).thenReturn(1);
    var request = marketCreate(true, "九成新", "当面交付");

    controller.market(principal, request);

    var parameters = org.mockito.ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(startsWith("insert into community_posts"), parameters.capture());
    assertEquals("九成新", parameters.getValue()[13]);
    assertEquals("当面交付", parameters.getValue()[14]);
    assertNotNull(parameters.getValue()[15]);
  }

  @Test
  void marketPostUpdatePreservesNewFieldsOmittedByOlderClients() {
    UUID post = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select author_user_id"), eq(post), eq("MARKETPLACE")))
        .thenReturn(List.of(Map.of("author_user_id", user)));
    when(jdbc.update(startsWith("update community_posts"), any(Object[].class))).thenReturn(1);
    var request = new CommunityController.Create(
        "球拍", "更新后的说明", "PUBLIC", null, null, null, null, "contact", "100", null, null, false);

    controller.updateMarket(principal, post, request);

    verify(jdbc).update(contains("marketplace_condition=coalesce(?,marketplace_condition)"), any(Object[].class));
  }

  @Test
  void marketReportRejectsOwnPostAndDuplicatePendingReport() {
    UUID post = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select id,author_user_id,status"), eq(post)))
        .thenReturn(List.of(Map.of("author_user_id", user, "status", "ON_SALE")));
    var request = new CommunityController.MarketReportRequest(
        CommunityController.MarketReportReason.SUSPECTED_FRAUD, "付款后要求提供验证码");
    assertEquals("MARKET_REPORT_SELF",
        assertThrows(BusinessException.class, () -> controller.reportMarketPost(principal, post, request)).code());
    verify(jdbc, never()).update(startsWith("insert into marketplace_reports"), any(Object[].class));

    UUID author = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select id,author_user_id,status"), eq(post)))
        .thenReturn(List.of(Map.of("author_user_id", author, "status", "ON_SALE")));
    when(jdbc.update(startsWith("insert into marketplace_reports"), any(Object[].class)))
        .thenThrow(new DuplicateKeyException("duplicate"));
    assertEquals("MARKET_REPORT_EXISTS",
        assertThrows(BusinessException.class, () -> controller.reportMarketPost(principal, post, request)).code());
  }

  @Test
  void marketReportCannotBeSubmittedForMissingOrHiddenPost() {
    UUID post = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select id,author_user_id,status"), eq(post)))
        .thenReturn(List.of(Map.of("author_user_id", UUID.randomUUID(), "status", "MODERATION_HIDDEN")));

    assertEquals("MARKET_POST_NOT_FOUND",
        assertThrows(BusinessException.class, () -> controller.reportMarketPost(principal, post,
            new CommunityController.MarketReportRequest(CommunityController.MarketReportReason.OTHER, null))).code());
    verify(jdbc, never()).update(startsWith("insert into marketplace_reports"), any(Object[].class));
  }

  @Test
  void pendingMarketReportsRequireModerationPermission() {
    when(jdbc.queryForList(startsWith("select r.id report_id"))).thenReturn(List.of());

    controller.pendingMarketReports(principal);

    verify(authorization).require(user, Permission.MARKETPLACE_MODERATE);
  }

  @Test
  void hidingMarketReportHidesPostAndWritesAuditAndNotification() {
    UUID reportId = UUID.randomUUID();
    UUID postId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select r.post_id,r.status,p.author_user_id"), eq(reportId)))
        .thenReturn(List.of(Map.of("post_id", postId, "status", "PENDING", "author_user_id", authorId)));
    when(jdbc.update(startsWith("update community_posts"), any(Object[].class))).thenReturn(1);

    controller.resolveMarketReport(principal, reportId,
        new CommunityController.MarketReportResolution(CommunityController.MarketReportAction.HIDE, "核实存在风险"));

    verify(authorization).require(user, Permission.MARKETPLACE_MODERATE);
    verify(jdbc).update(contains("status='MODERATION_HIDDEN'"), any(Object[].class));
    verify(jdbc).update(startsWith("insert into notifications"), any(Object[].class));
    verify(jdbc).update(startsWith("update marketplace_reports"), any(Object[].class));
    verify(jdbc).update(startsWith("insert into audit_logs"), any(Object[].class));
  }

  @Test
  void dismissingMarketReportDoesNotHidePostOrNotifyAuthor() {
    UUID reportId = UUID.randomUUID();
    UUID postId = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select r.post_id,r.status,p.author_user_id"), eq(reportId)))
        .thenReturn(List.of(Map.of("post_id", postId, "status", "PENDING", "author_user_id", UUID.randomUUID())));

    controller.resolveMarketReport(principal, reportId,
        new CommunityController.MarketReportResolution(CommunityController.MarketReportAction.DISMISS, null));

    verify(jdbc, never()).update(contains("status='MODERATION_HIDDEN'"), any(Object[].class));
    verify(jdbc, never()).update(startsWith("insert into notifications"), any(Object[].class));
    verify(jdbc).update(startsWith("update marketplace_reports"), any(Object[].class));
    verify(jdbc).update(startsWith("insert into audit_logs"), any(Object[].class));
  }

  @Test
  void alreadyReviewedMarketReportCannotBeProcessedTwice() {
    UUID reportId = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select r.post_id,r.status,p.author_user_id"), eq(reportId)))
        .thenReturn(List.of(Map.of("post_id", UUID.randomUUID(), "status", "REVIEWED", "author_user_id", UUID.randomUUID())));

    assertEquals("MARKET_REPORT_ALREADY_REVIEWED", assertThrows(BusinessException.class,
        () -> controller.resolveMarketReport(principal, reportId,
            new CommunityController.MarketReportResolution(CommunityController.MarketReportAction.HIDE, null))).code());
    verify(jdbc, never()).update(startsWith("update marketplace_reports"), any(Object[].class));
  }

  @Test
  void restoringHiddenPostRestoresStateAndWritesAuditAndNotification() {
    UUID postId = UUID.randomUUID();
    when(jdbc.update(startsWith("update community_posts"), any(Object[].class))).thenReturn(1);

    controller.restoreHiddenMarketPost(principal, postId,
        new CommunityController.MarketPostRestoreRequest("信息已核实"));

    verify(authorization).require(user, Permission.MARKETPLACE_MODERATE);
    verify(jdbc).update(contains("moderation_previous_status=null"), any(Object[].class));
    verify(jdbc).update(startsWith("insert into notifications"), any(Object[].class));
    verify(jdbc).update(startsWith("insert into audit_logs"), any(Object[].class));
  }

  @Test
  void cannotRestorePostThatIsNotHidden() {
    UUID postId = UUID.randomUUID();
    when(jdbc.update(startsWith("update community_posts"), any(Object[].class))).thenReturn(0);

    assertEquals("MARKET_POST_NOT_HIDDEN", assertThrows(BusinessException.class,
        () -> controller.restoreHiddenMarketPost(principal, postId, null)).code());
    verify(jdbc, never()).update(startsWith("insert into audit_logs"), any(Object[].class));
  }

  @Test
  void ownerCannotMarkModerationHiddenPostSold() {
    UUID postId = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select author_user_id"), eq(postId), eq("MARKETPLACE")))
        .thenReturn(List.of(Map.of("author_user_id", user)));
    when(jdbc.update(contains("status in ('ON_SALE','PUBLISHED')"), eq(postId))).thenReturn(0);

    assertEquals("MARKET_POST_NOT_EDITABLE",
        assertThrows(BusinessException.class, () -> controller.markSold(principal, postId)).code());
  }

  @Test
  void publicMarketListExcludesModerationHiddenPosts() {
    when(jdbc.queryForList(contains("p.status<>'MODERATION_HIDDEN'"), eq("MARKETPLACE"), eq(false)))
        .thenReturn(List.of());

    controller.marketPosts(null);

    verify(jdbc).queryForList(contains("p.status<>'MODERATION_HIDDEN'"), eq("MARKETPLACE"), eq(false));
  }

  @Test
  void hiddenMarketPostCannotBeReadByPublicDetailEndpoint() {
    UUID postId = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select p.*"), eq(postId)))
        .thenReturn(List.of(Map.of("post_type", "MARKETPLACE", "status", "MODERATION_HIDDEN")));

    assertEquals("MARKET_POST_NOT_FOUND",
        assertThrows(BusinessException.class, () -> controller.detail(null, postId)).code());
  }

  @Test
  void readAllNotificationsMarksOnlyCurrentUserNotifications() {
    when(jdbc.update(startsWith("update notifications set read_at"), any(Timestamp.class), eq(user))).thenReturn(2);

    controller.readAll(principal);

    verify(jdbc).update(startsWith("update notifications set read_at"), any(Timestamp.class), eq(user));
  }

  private static CommunityController.Create create(Integer maxParticipants) {
    return new CommunityController.Create(
        "标题", "内容", "PUBLIC", null, maxParticipants, Instant.now(), "球馆", "contact", "10", null, null, false);
  }

  private static CommunityController.Create marketCreate(boolean rulesAccepted, String condition, String delivery) {
    return new CommunityController.Create(
        "球拍", "物品说明", "PUBLIC", null, null, null, null, "contact", "100", condition, delivery, rulesAccepted);
  }

  private static Map<String, Object> playPost(UUID id, int maxParticipants) {
    return new HashMap<>(Map.of("id", id, "post_type", "MEETUP", "status", "PUBLISHED", "max_participants", maxParticipants));
  }
}
