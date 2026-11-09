package org.opensourcepingpong.community;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.ApiResponse;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ForumController {
  private static final String POST_FIELDS =
      "id, author_user_id, category, title, content, status, created_at, updated_at";
  private final JdbcTemplate jdbc;
  private final AuthorizationService authorization;

  public ForumController(JdbcTemplate jdbc, AuthorizationService authorization) {
    this.jdbc = jdbc;
    this.authorization = authorization;
  }

  @GetMapping("/forum/posts")
  ApiResponse<Page> posts(@RequestParam(required = false) String category,
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    String source = " from forum_posts where status='PUBLISHED'";
    if (category == null) {
      return page(POST_FIELDS, source, "created_at desc, id desc", page, size);
    }
    if (category.isBlank() || category.length() > 40) throw invalidPageOrCategory();
    return page(POST_FIELDS, source + " and category=?", "created_at desc, id desc", page, size, category.strip());
  }

  @GetMapping("/forum/posts/{postId}")
  ApiResponse<Map<String, Object>> post(@PathVariable UUID postId) {
    return ApiResponse.ok(visiblePost(postId, false));
  }

  @PostMapping("/forum/posts")
  ApiResponse<Map<String, UUID>> createPost(@AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody PostInput input) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        insert into forum_posts(id, author_user_id, category, title, content) values(?,?,?,?,?)
        """, id, principal.userId(), input.category().strip(), input.title().strip(), input.content().strip());
    return ApiResponse.ok(Map.of("id", id));
  }

  @PutMapping("/forum/posts/{postId}")
  @Transactional
  ApiResponse<Void> editPost(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID postId,
      @Valid @RequestBody PostInput input) {
    requireAuthor(principal.userId(), visiblePost(postId, true));
    jdbc.update("""
        update forum_posts set category=?, title=?, content=?, updated_at=current_timestamp where id=?
        """, input.category().strip(), input.title().strip(), input.content().strip(), postId);
    return ApiResponse.ok(null);
  }

  @DeleteMapping("/forum/posts/{postId}")
  @Transactional
  ApiResponse<Void> deletePost(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID postId) {
    requireAuthor(principal.userId(), visiblePost(postId, true));
    jdbc.update("update forum_posts set status='DELETED', updated_at=current_timestamp where id=?", postId);
    return ApiResponse.ok(null);
  }

  @GetMapping("/forum/posts/{postId}/comments")
  @Transactional
  ApiResponse<Page> comments(@PathVariable UUID postId, @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    visiblePost(postId, true);
    return page("id, post_id, author_user_id, content, created_at",
        " from forum_comments where post_id=? and deleted_at is null", "created_at, id", page, size, postId);
  }

  @PostMapping("/forum/posts/{postId}/comments")
  @Transactional
  ApiResponse<Map<String, UUID>> createComment(@AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID postId, @Valid @RequestBody CommentInput input) {
    visiblePost(postId, true);
    UUID id = UUID.randomUUID();
    jdbc.update("insert into forum_comments(id, post_id, author_user_id, content) values(?,?,?,?)",
        id, postId, principal.userId(), input.content().strip());
    return ApiResponse.ok(Map.of("id", id));
  }

  @DeleteMapping("/forum/posts/{postId}/comments/{commentId}")
  @Transactional
  ApiResponse<Void> deleteComment(@AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID postId, @PathVariable UUID commentId) {
    visiblePost(postId, true);
    var comment = one("""
        select author_user_id from forum_comments where id=? and post_id=? and deleted_at is null for update
        """, "FORUM_COMMENT_NOT_FOUND", commentId, postId);
    if (!principal.userId().equals(comment.get("author_user_id"))) {
      authorization.require(principal.userId(), Permission.FORUM_MODERATE);
    }
    jdbc.update("update forum_comments set deleted_at=current_timestamp where id=? and post_id=?", commentId, postId);
    return ApiResponse.ok(null);
  }

  @PostMapping("/forum/posts/{postId}/reports")
  @Transactional
  ApiResponse<Map<String, UUID>> report(@AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID postId, @Valid @RequestBody ReportInput input) {
    visiblePost(postId, true);
    UUID id = UUID.randomUUID();
    int inserted = jdbc.update("""
        insert into forum_reports(id, post_id, reporter_user_id, reason)
        values(?,?,?,?) on conflict (post_id, reporter_user_id) do nothing
        """, id, postId, principal.userId(), input.reason().strip());
    if (inserted == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, "FORUM_REPORT_DUPLICATE", "已举报过该帖子");
    }
    return ApiResponse.ok(Map.of("id", id));
  }

  @GetMapping("/forum/reports/{reportId}")
  ApiResponse<Map<String, Object>> reportStatus(@AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID reportId) {
    return ApiResponse.ok(one("""
        select id, post_id, reason, status, created_at, reviewed_at
        from forum_reports where id=? and reporter_user_id=?
        """, "FORUM_REPORT_NOT_FOUND", reportId, principal.userId()));
  }

  @GetMapping("/admin/forum/reports")
  ApiResponse<Page> reports(@AuthenticationPrincipal AuthPrincipal principal,
      @RequestParam(defaultValue = "PENDING") ReportStatus status, @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    authorization.require(principal.userId(), Permission.FORUM_MODERATE);
    return page("""
        r.id, r.post_id, r.reporter_user_id, r.reason, r.status, r.created_at,
        r.reviewed_by, r.reviewed_at, r.review_note, p.title as post_title,
        p.content as post_content, p.status as post_status
        """, " from forum_reports r join forum_posts p on p.id=r.post_id where r.status=?",
        "r.created_at desc, r.id desc", page, size, status.name());
  }

  @PutMapping("/admin/forum/reports/{reportId}")
  @Transactional
  ApiResponse<Void> review(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID reportId,
      @Valid @RequestBody ReviewInput input) {
    UUID moderator = principal.userId();
    authorization.require(moderator, Permission.FORUM_MODERATE);
    var report = one("select post_id, status from forum_reports where id=? for update",
        "FORUM_REPORT_NOT_FOUND", reportId);
    if (!"PENDING".equals(report.get("status"))) {
      throw new BusinessException(HttpStatus.CONFLICT, "FORUM_REPORT_ALREADY_REVIEWED", "举报已处理");
    }
    ReportStatus status = input.action() == ReviewAction.HIDE ? ReportStatus.HIDDEN : ReportStatus.DISMISSED;
    if (status == ReportStatus.HIDDEN) {
      jdbc.update("""
          update forum_posts set status='HIDDEN', updated_at=current_timestamp
          where id=? and status='PUBLISHED'
          """, report.get("post_id"));
    }
    jdbc.update("""
        update forum_reports set status=?, reviewed_by=?, reviewed_at=current_timestamp, review_note=?
        where id=?
        """, status.name(), moderator, input.note().strip(), reportId);
    jdbc.update("""
        insert into audit_logs(id, operator_user_id, action, target_type, target_id, reason) values(?,?,?,?,?,?)
        """, UUID.randomUUID(), moderator, "FORUM_REPORT_" + status.name(), "FORUM_REPORT", reportId, input.note().strip());
    return ApiResponse.ok(null);
  }

  private Map<String, Object> visiblePost(UUID id, boolean lock) {
    String sql = "select " + POST_FIELDS + " from forum_posts where id=? and status='PUBLISHED'";
    return one(lock ? sql + " for update" : sql, "FORUM_POST_NOT_FOUND", id);
  }

  private Map<String, Object> one(String sql, String errorCode, Object... args) {
    var rows = jdbc.queryForList(sql, args);
    if (rows.isEmpty()) {
      throw new BusinessException(HttpStatus.NOT_FOUND, errorCode, "找不到该论坛资源");
    }
    return rows.getFirst();
  }

  private static void requireAuthor(UUID userId, Map<String, Object> post) {
    if (!userId.equals(post.get("author_user_id"))) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "只有作者可修改或删除帖子");
    }
  }

  private ApiResponse<Page> page(String fields, String source, String order, int page, int size, Object... filters) {
    if (page < 1 || size < 1 || size > 100) throw invalidPageOrCategory();
    long total = jdbc.queryForObject("select count(*)" + source, Long.class, filters);
    var args = new ArrayList<>(List.of(filters));
    args.add(size);
    args.add((page - 1L) * size);
    var items = jdbc.queryForList("select " + fields + source + " order by " + order + " limit ? offset ?",
        args.toArray());
    return ApiResponse.ok(new Page(items, page, size, total, total / size + (total % size == 0 ? 0 : 1)));
  }

  private static BusinessException invalidPageOrCategory() {
    return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "分页或分类参数无效");
  }

  public record Page(List<Map<String, Object>> items, int page, int size, long total, long pages) {}
  public record PostInput(@NotBlank @Size(max = 40) String category, @NotBlank @Size(max = 160) String title,
      @NotBlank @Size(max = 5000) String content) {}
  public record CommentInput(@NotBlank @Size(max = 2000) String content) {}
  public record ReportInput(@NotBlank @Size(max = 500) String reason) {}
  public record ReviewInput(@NotNull ReviewAction action, @NotBlank @Size(max = 500) String note) {}
  public enum ReviewAction { HIDE, DISMISS }
  public enum ReportStatus { PENDING, HIDDEN, DISMISSED }
}
