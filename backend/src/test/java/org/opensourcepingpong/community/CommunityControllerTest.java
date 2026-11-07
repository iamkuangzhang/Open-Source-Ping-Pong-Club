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
        eventTime, "Gym table 1", "In-app contact", null);
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
  void readAllNotificationsMarksOnlyCurrentUserNotifications() {
    when(jdbc.update(startsWith("update notifications set read_at"), any(Timestamp.class), eq(user))).thenReturn(2);

    controller.readAll(principal);

    verify(jdbc).update(startsWith("update notifications set read_at"), any(Timestamp.class), eq(user));
  }

  private static CommunityController.Create create(Integer maxParticipants) {
    return new CommunityController.Create("标题", "内容", "PUBLIC", null, maxParticipants, Instant.now(), "球馆", "contact", "10");
  }

  private static Map<String, Object> playPost(UUID id, int maxParticipants) {
    return new HashMap<>(Map.of("id", id, "post_type", "MEETUP", "status", "PUBLISHED", "max_participants", maxParticipants));
  }
}
