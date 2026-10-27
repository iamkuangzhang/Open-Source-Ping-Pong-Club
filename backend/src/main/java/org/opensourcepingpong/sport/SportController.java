package org.opensourcepingpong.sport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.ApiResponse;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SportController {
  private final JdbcTemplate jdbc;
  private final AuthorizationService authorization;

  public SportController(JdbcTemplate jdbc, AuthorizationService authorization) {
    this.jdbc = jdbc;
    this.authorization = authorization;
  }

  @GetMapping("/sports")
  ApiResponse<?> list() {
    return ApiResponse.ok(jdbc.queryForList(
        "select id,code,name from sports where active=true order by sort_order,name"));
  }

  @PostMapping("/admin/sports")
  ApiResponse<?> create(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody CreateSport request) {
    authorization.require(principal.userId(), Permission.SPORTS_MANAGE);
    String code = request.code().trim().toUpperCase(Locale.ROOT);
    String name = request.name().trim();
    if (jdbc.queryForList("select id from sports where code=?", code).size() > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, "SPORT_ALREADY_EXISTS", "运动类型编码已存在");
    }

    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    try {
      jdbc.update("""
          insert into sports(id,code,name,active,created_by,created_at,updated_at)
          values(?,?,?,true,?,?,?)
          """, id, code, name, principal.userId(), Timestamp.from(now), Timestamp.from(now));
    } catch (DuplicateKeyException exception) {
      throw new BusinessException(HttpStatus.CONFLICT, "SPORT_ALREADY_EXISTS", "运动类型编码已存在");
    }
    jdbc.update("""
        insert into audit_logs(id,operator_user_id,action,target_type,target_id,after_json,created_at)
        values(?,?,?,'SPORT',?,jsonb_build_object('code',?,'name',?),?)
        """, UUID.randomUUID(), principal.userId(), "SPORT_CREATED", id, code, name, Timestamp.from(now));
    return ApiResponse.ok(Map.of("id", id, "code", code, "name", name));
  }

  public record CreateSport(
      @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,39}") String code,
      @NotBlank @Size(max = 40) String name) {}

  @PutMapping("/admin/sports/{id}")
  ApiResponse<?> update(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateSport request) {
    authorization.require(principal.userId(), Permission.SPORTS_MANAGE);
    Instant now = Instant.now();
    int changed = jdbc.update("""
        update sports
        set name=?,sort_order=?,updated_at=?
        where id=?
        """, request.name().trim(), request.sortOrder(), Timestamp.from(now), id);
    if (changed != 1) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "SPORT_NOT_FOUND", "运动类型不存在");
    }
    jdbc.update("""
        insert into audit_logs(id,operator_user_id,action,target_type,target_id,after_json,created_at)
        values(?,?,?,'SPORT',?,jsonb_build_object('name',?,'sortOrder',?),?)
        """, UUID.randomUUID(), principal.userId(), "SPORT_UPDATED", id,
        request.name().trim(), request.sortOrder(), Timestamp.from(now));
    return ApiResponse.ok(null);
  }

  public record UpdateSport(
      @NotBlank @Size(max = 40) String name,
      @NotNull @Min(0) Integer sortOrder) {}

  @PutMapping("/admin/sports/{id}/status")
  ApiResponse<?> updateStatus(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody SportStatus request) {
    authorization.require(principal.userId(), Permission.SPORTS_MANAGE);
    Instant now = Instant.now();
    int changed = jdbc.update(
        "update sports set active=?,updated_at=? where id=?",
        request.active(), Timestamp.from(now), id);
    if (changed != 1) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "SPORT_NOT_FOUND", "运动类型不存在");
    }
    jdbc.update("""
        insert into audit_logs(id,operator_user_id,action,target_type,target_id,after_json,created_at)
        values(?,?,?,'SPORT',?,jsonb_build_object('active',?),?)
        """, UUID.randomUUID(), principal.userId(),
        request.active() ? "SPORT_ENABLED" : "SPORT_DISABLED", id, request.active(), Timestamp.from(now));
    return ApiResponse.ok(null);
  }

  public record SportStatus(@NotNull Boolean active) {}
}
