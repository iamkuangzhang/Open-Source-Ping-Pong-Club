package org.opensourcepingpong.community;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.ApiResponse;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CommunityController {
  private final JdbcTemplate jdbc;
  private final AuthorizationService auth;

  public CommunityController(JdbcTemplate jdbc, AuthorizationService auth) {
    this.jdbc = jdbc;
    this.auth = auth;
  }

  @GetMapping("/announcements")
  ApiResponse<?> announcements(@AuthenticationPrincipal AuthPrincipal principal) {
    return posts("ANNOUNCEMENT", principal, false);
  }

  @GetMapping({"/play-posts", "/meetups"})
  ApiResponse<?> playPosts(@AuthenticationPrincipal AuthPrincipal principal) {
    return posts("MEETUP", principal, false);
  }

  @GetMapping({"/market-posts", "/marketplace"})
  ApiResponse<?> marketPosts(@AuthenticationPrincipal AuthPrincipal principal) {
    return posts("MARKETPLACE", principal, true);
  }

  @GetMapping({"/announcements/{id}", "/play-posts/{id}", "/meetups/{id}", "/market-posts/{id}", "/marketplace/{id}"})
  ApiResponse<?> detail(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
    var rows = jdbc.queryForList("""
        select p.*,
          (select count(*) from meetup_participants mp where mp.post_id=p.id) as participant_count
        from community_posts p
        where p.id=?
        """, id);
    if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_FOUND", "post not found");
    var post = rows.getFirst();
    if (!visible(post, principal)) throw new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "post is members only");
    return ApiResponse.ok(post);
  }

  @PostMapping({"/admin/announcements", "/announcements"})
  ApiResponse<Map<String, UUID>> announcement(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody Create r) {
    auth.require(p.userId(), Permission.ANNOUNCEMENT_MANAGE);
    return create(p.userId(), "ANNOUNCEMENT", "PUBLISHED", r);
  }

  @PutMapping("/admin/announcements/{id}")
  ApiResponse<Void> updateAnnouncement(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody Create r) {
    auth.require(p.userId(), Permission.ANNOUNCEMENT_MANAGE);
    int changed = jdbc.update("""
        update community_posts
        set title=?,content=?,visibility=?,expires_at=?,updated_at=current_timestamp
        where id=? and post_type='ANNOUNCEMENT'
        """, r.title(), r.content(), visibility(r.visibility()), sqlTimestamp(r.expiresAt()), id);
    if (changed != 1) throw new BusinessException(HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_FOUND", "announcement not found");
    return ApiResponse.ok(null);
  }

  @DeleteMapping("/admin/announcements/{id}")
  ApiResponse<Void> deleteAnnouncement(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) {
    auth.require(p.userId(), Permission.ANNOUNCEMENT_MANAGE);
    int changed = jdbc.update("update community_posts set status='DELETED',updated_at=current_timestamp where id=? and post_type='ANNOUNCEMENT'", id);
    if (changed != 1) throw new BusinessException(HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_FOUND", "announcement not found");
    return ApiResponse.ok(null);
  }

  @PostMapping({"/play-posts", "/meetups"})
  ApiResponse<Map<String, UUID>> playPost(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody Create r) {
    if (r.maxParticipants() == null || r.maxParticipants() < 1) {
      throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "maxParticipants is required");
    }
    return create(p.userId(), "MEETUP", "PUBLISHED", r);
  }

  @PutMapping({"/play-posts/{id}", "/meetups/{id}"})
  ApiResponse<Void> updatePlayPost(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody Create r) {
    requireOwner(id, p.userId(), "MEETUP", "PLAY_POST_NOT_FOUND", "PLAY_POST_NOT_OWNER");
    int changed = jdbc.update("""
        update community_posts
        set title=?,content=?,visibility=?,expires_at=?,max_participants=?,event_time=?,location=?,contact=?,updated_at=current_timestamp
        where id=? and post_type='MEETUP' and status='PUBLISHED'
        """, r.title(), r.content(), visibility(r.visibility()), sqlTimestamp(r.expiresAt()), r.maxParticipants(), sqlTimestamp(r.eventTime()), r.location(), r.contact(), id);
    if (changed != 1) throw new BusinessException(HttpStatus.CONFLICT, "PLAY_POST_NOT_OPEN", "play post is not open");
    return ApiResponse.ok(null);
  }

  @PostMapping({"/play-posts/{id}/participants", "/meetups/{id}/participants"})
  @Transactional
  ApiResponse<Void> joinPlayPost(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) {
    var post = one("select id,post_type,status,max_participants from community_posts where id=? for update", id);
    if (!"MEETUP".equals(post.get("post_type")) || !"PUBLISHED".equals(post.get("status"))) throw playNotOpen();
    int max = ((Number) post.get("max_participants")).intValue();
    int count = jdbc.queryForObject("select count(*) from meetup_participants where post_id=?", Integer.class, id);
    if (count >= max) throw new BusinessException(HttpStatus.CONFLICT, "PLAY_POST_FULL", "play post is full");
    try {
      jdbc.update("insert into meetup_participants(id,post_id,user_id) values(?,?,?)", UUID.randomUUID(), id, p.userId());
    } catch (DuplicateKeyException e) {
      throw new BusinessException(HttpStatus.CONFLICT, "PLAY_ALREADY_JOINED", "already joined");
    }
    return ApiResponse.ok(null);
  }

  @DeleteMapping({"/play-posts/{id}/participants/me", "/meetups/{id}/participants/me"})
  ApiResponse<Void> leavePlayPost(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) {
    jdbc.update("delete from meetup_participants where post_id=? and user_id=?", id, p.userId());
    return ApiResponse.ok(null);
  }

  @PostMapping({"/market-posts", "/marketplace"})
  ApiResponse<Map<String, UUID>> market(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody Create r) {
    return create(p.userId(), "MARKETPLACE", "ON_SALE", r);
  }

  @PutMapping({"/market-posts/{id}", "/marketplace/{id}"})
  ApiResponse<Void> updateMarket(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody Create r) {
    requireOwner(id, p.userId(), "MARKETPLACE", "MARKET_POST_NOT_FOUND", "MARKET_POST_NOT_OWNER");
    int changed = jdbc.update("""
        update community_posts
        set title=?,content=?,visibility=?,expires_at=?,price=?,contact=?,updated_at=current_timestamp
        where id=? and post_type='MARKETPLACE' and status <> 'SOLD'
        """, r.title(), r.content(), visibility(r.visibility()), sqlTimestamp(r.expiresAt()), r.price(), r.contact(), id);
    if (changed != 1) throw new BusinessException(HttpStatus.CONFLICT, "MARKET_POST_SOLD", "sold post cannot be edited");
    return ApiResponse.ok(null);
  }

  @PostMapping({"/market-posts/{id}/mark-sold", "/marketplace/{id}/mark-sold"})
  ApiResponse<Void> markSold(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) {
    requireOwner(id, p.userId(), "MARKETPLACE", "MARKET_POST_NOT_FOUND", "MARKET_POST_NOT_OWNER");
    jdbc.update("update community_posts set status='SOLD',updated_at=current_timestamp where id=? and post_type='MARKETPLACE'", id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/notifications")
  ApiResponse<?> notifications(@AuthenticationPrincipal AuthPrincipal p) {
    return ApiResponse.ok(jdbc.queryForList("select id,notification_type,title,content,read_at,created_at from notifications where user_id=? order by created_at desc", p.userId()));
  }

  @PostMapping("/notifications/{id}/read")
  ApiResponse<Void> read(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) {
    if (jdbc.update("update notifications set read_at=? where id=? and user_id=? and read_at is null", sqlTimestamp(Instant.now()), id, p.userId()) == 0) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "notification not found");
    }
    return ApiResponse.ok(null);
  }

  @PostMapping("/notifications/read-all")
  ApiResponse<Void> readAll(@AuthenticationPrincipal AuthPrincipal p) {
    jdbc.update("update notifications set read_at=? where user_id=? and read_at is null", sqlTimestamp(Instant.now()), p.userId());
    return ApiResponse.ok(null);
  }

  private ApiResponse<?> posts(String type, AuthPrincipal principal, boolean includeSold) {
    boolean canSeeMembersOnly = principal != null && activeMember(principal.userId());
    String statusFilter = includeSold ? "status in ('ON_SALE','SOLD','PUBLISHED')" : "status='PUBLISHED'";
    return ApiResponse.ok(jdbc.queryForList("""
        select p.id,p.post_type,p.title,p.content,p.author_user_id,p.status,p.visibility,p.expires_at,p.max_participants,
               p.event_time,p.location,p.contact,p.price,p.created_at,
               (select count(*) from meetup_participants mp where mp.post_id=p.id) as participant_count
        from community_posts p
        where p.post_type=? and %s and (p.expires_at is null or p.expires_at>current_timestamp)
          and (p.visibility='PUBLIC' or ?)
        order by p.created_at desc
        """.formatted(statusFilter), type, canSeeMembersOnly));
  }

  private ApiResponse<Map<String, UUID>> create(UUID user, String type, String status, Create r) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        insert into community_posts(id,post_type,title,content,author_user_id,status,visibility,expires_at,max_participants,event_time,location,contact,price)
        values(?,?,?,?,?,?,?,?,?,?,?,?,?)
        """, id, type, r.title(), r.content(), user, status, visibility(r.visibility()), sqlTimestamp(r.expiresAt()), r.maxParticipants(), sqlTimestamp(r.eventTime()), r.location(), r.contact(), r.price());
    return ApiResponse.ok(Map.of("id", id));
  }

  private boolean visible(Map<String, Object> post, AuthPrincipal principal) {
    if (!"MEMBERS_ONLY".equals(post.get("visibility"))) return true;
    return principal != null && activeMember(principal.userId());
  }

  private boolean activeMember(UUID user) {
    return jdbc.queryForList("select id from memberships where user_id=? and status='ACTIVE'", user).size() == 1;
  }

  private void requireOwner(UUID id, UUID user, String type, String missingCode, String ownerCode) {
    var rows = jdbc.queryForList("select author_user_id from community_posts where id=? and post_type=?", id, type);
    if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, missingCode, "post not found");
    if (!user.equals(rows.getFirst().get("author_user_id"))) throw new BusinessException(HttpStatus.FORBIDDEN, ownerCode, "not post owner");
  }

  private Map<String, Object> one(String sql, Object... args) {
    var rows = jdbc.queryForList(sql, args);
    if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_FOUND", "post not found");
    return rows.getFirst();
  }

  private static BusinessException playNotOpen() {
    return new BusinessException(HttpStatus.CONFLICT, "PLAY_POST_NOT_OPEN", "play post is not open");
  }

  private static String visibility(String value) {
    return "MEMBERS_ONLY".equals(value) ? "MEMBERS_ONLY" : "PUBLIC";
  }

  private static Timestamp sqlTimestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  public record Create(
      @NotBlank @Size(max = 160) String title,
      @NotBlank @Size(max = 5000) String content,
      String visibility,
      Instant expiresAt,
      @Min(1) Integer maxParticipants,
      Instant eventTime,
      @Size(max = 200) String location,
      @Size(max = 120) String contact,
      @Size(max = 60) String price) {}
}
