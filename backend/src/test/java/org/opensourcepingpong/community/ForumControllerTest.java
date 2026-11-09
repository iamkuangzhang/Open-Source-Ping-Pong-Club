package org.opensourcepingpong.community;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class ForumControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final ForumController forum = new ForumController(jdbc, authorization);
  private final UUID userId = UUID.randomUUID();
  private final UUID postId = UUID.randomUUID();
  private final AuthPrincipal user = new AuthPrincipal(userId, "user");

  @Test
  void categoryPageOnlyIncludesPublishedPostsAndUsesStableOrdering() {
    when(jdbc.queryForObject(contains("status='PUBLISHED' and category=?"), eq(Long.class), eq("训练")))
        .thenReturn(21L);

    var page = forum.posts("训练", 2, 20).data();

    assertEquals(21L, page.total());
    assertEquals(2L, page.pages());
    verify(jdbc).queryForList(contains("order by created_at desc, id desc limit ? offset ?"),
        eq("训练"), eq(20), eq(20L));
  }

  @Test
  void missingOrModeratedPostIsUnavailableToDetailAndCommentList() {
    assertEquals("FORUM_POST_NOT_FOUND", assertThrows(BusinessException.class, () -> forum.post(postId)).code());
    assertEquals("FORUM_POST_NOT_FOUND",
        assertThrows(BusinessException.class, () -> forum.comments(postId, 1, 20)).code());
    verify(jdbc).queryForList(endsWith("where id=? and status='PUBLISHED'"), eq(postId));
    verify(jdbc, never()).queryForObject(contains("forum_comments"), eq(Long.class), any(Object[].class));
  }

  @Test
  void nonAuthorCannotEditOrDeletePost() {
    publishedPost(UUID.randomUUID());

    assertEquals("PERMISSION_DENIED", assertThrows(BusinessException.class,
        () -> forum.editPost(user, postId, new ForumController.PostInput("训练", "标题", "正文"))).code());
    assertEquals("PERMISSION_DENIED", assertThrows(BusinessException.class,
        () -> forum.deletePost(user, postId)).code());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void deletingAnotherUsersCommentRequiresModerationPermission() {
    publishedPost(userId);
    UUID commentId = UUID.randomUUID();
    when(jdbc.queryForList(contains("where id=? and post_id=?"), eq(commentId), eq(postId)))
        .thenReturn(List.of(Map.of("author_user_id", UUID.randomUUID())));
    doThrow(new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "denied"))
        .when(authorization).require(userId, Permission.FORUM_MODERATE);

    assertEquals("PERMISSION_DENIED",
        assertThrows(BusinessException.class, () -> forum.deleteComment(user, postId, commentId)).code());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void repeatedReportReturnsConflict() {
    publishedPost(UUID.randomUUID());
    assertEquals("FORUM_REPORT_DUPLICATE", assertThrows(BusinessException.class,
        () -> forum.report(user, postId, new ForumController.ReportInput("不当内容"))).code());
    verify(jdbc).update(contains("on conflict (post_id, reporter_user_id) do nothing"),
        any(UUID.class), eq(postId), eq(userId), eq("不当内容"));
  }

  @Test
  void moderatorCanHidePostAndLeaveAuditTrail() {
    UUID reportId = UUID.randomUUID();
    when(jdbc.queryForList(contains("from forum_reports where id=? for update"), eq(reportId)))
        .thenReturn(List.of(Map.of("post_id", postId, "status", "PENDING")));

    forum.review(user, reportId, new ForumController.ReviewInput(ForumController.ReviewAction.HIDE, "已确认"));

    verify(authorization).require(userId, Permission.FORUM_MODERATE);
    verify(jdbc).update(contains("status='HIDDEN'"), eq(postId));
    verify(jdbc).update(contains("reviewed_by=?"), eq("HIDDEN"), eq(userId), eq("已确认"), eq(reportId));
    verify(jdbc).update(contains("insert into audit_logs"), any(UUID.class), eq(userId),
        eq("FORUM_REPORT_HIDDEN"), eq("FORUM_REPORT"), eq(reportId), eq("已确认"));
  }

  private void publishedPost(UUID author) {
    when(jdbc.queryForList(contains("status='PUBLISHED' for update"), eq(postId)))
        .thenReturn(List.of(Map.of("author_user_id", author)));
  }
}
