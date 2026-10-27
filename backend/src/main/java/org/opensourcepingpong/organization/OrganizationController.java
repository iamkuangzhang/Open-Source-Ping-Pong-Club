package org.opensourcepingpong.organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.ApiResponse;
import org.opensourcepingpong.common.api.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrganizationController {
  private final JdbcTemplate jdbc;
  private final AuthorizationService authorization;

  public OrganizationController(JdbcTemplate jdbc, AuthorizationService authorization) {
    this.jdbc = jdbc;
    this.authorization = authorization;
  }

  @GetMapping("/organization/roles")
  ApiResponse<?> roles() {
    return ApiResponse.ok(jdbc.queryForList("select code,name from organization_roles order by code"));
  }

  @GetMapping("/organization/me/roles")
  ApiResponse<?> myRoles(@AuthenticationPrincipal AuthPrincipal principal) {
    return ApiResponse.ok(authorization.roles(principal.userId()));
  }

  @GetMapping("/members/{userId}/roles")
  ApiResponse<?> memberRoles(@PathVariable UUID userId) {
    return ApiResponse.ok(authorization.roles(userId));
  }

  @GetMapping("/admin/members")
  ApiResponse<?> members(@AuthenticationPrincipal AuthPrincipal principal) {
    authorization.require(principal.userId(), Permission.ROLE_MANAGE);
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select m.user_id,m.member_number,p.nickname,r.code role_code
        from memberships m
        left join user_profiles p on p.user_id=m.user_id
        left join user_organization_roles ur on ur.user_id=m.user_id and ur.revoked_at is null
        left join organization_roles r on r.id=ur.role_id
        where m.status='ACTIVE'
        order by p.nickname,m.member_number,r.code
        """);
    Map<UUID, MemberRoles> members = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      UUID userId = (UUID) row.get("user_id");
      MemberRoles member = members.computeIfAbsent(userId, ignored ->
          new MemberRoles(userId, (String) row.get("nickname"), (String) row.get("member_number")));
      String role = (String) row.get("role_code");
      if (role != null) {
        member.roles().add(RoleCode.valueOf(role));
      }
    }
    return ApiResponse.ok(members.values().stream()
        .map(member -> new MemberView(member.userId(), member.nickname(), member.memberNumber(), List.copyOf(member.roles())))
        .toList());
  }

  @PutMapping("/admin/members/{userId}/roles")
  @Transactional
  ApiResponse<?> updateRole(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID userId,
      @Valid @RequestBody RoleUpdate request) {
    authorization.require(principal.userId(), Permission.ROLE_MANAGE);
    if (jdbc.queryForList("select id from memberships where user_id=? and status='ACTIVE'", userId).isEmpty()) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "有效成员不存在");
    }
    UUID roleId = jdbc.queryForList("select id from organization_roles where code=?", request.roleCode().name())
        .stream()
        .findFirst()
        .map(row -> (UUID) row.get("id"))
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "职务不存在"));

    Instant now = Instant.now();
    if (request.active()) {
      jdbc.update("""
          insert into user_organization_roles(user_id,role_id,granted_by,granted_at)
          values(?,?,?,?)
          on conflict(user_id,role_id) do update
          set granted_by=excluded.granted_by,granted_at=excluded.granted_at,revoked_by=null,revoked_at=null
          """, userId, roleId, principal.userId(), Timestamp.from(now));
    } else {
      jdbc.update("""
          update user_organization_roles
          set revoked_by=?,revoked_at=?
          where user_id=? and role_id=? and revoked_at is null
          """, principal.userId(), Timestamp.from(now), userId, roleId);
    }
    jdbc.update("""
        insert into audit_logs(id,operator_user_id,action,target_type,target_id,after_json,created_at)
        values(?,?,?,?,?,jsonb_build_object('role',?,'active',?),?)
        """, UUID.randomUUID(), principal.userId(), request.active() ? "ROLE_GRANTED" : "ROLE_REVOKED",
        "USER_ROLE", userId, request.roleCode().name(), request.active(), Timestamp.from(now));
    return ApiResponse.ok(authorization.roles(userId));
  }

  public record RoleUpdate(@NotNull RoleCode roleCode, boolean active) {}

  public record MemberView(UUID userId, String nickname, String memberNumber, List<RoleCode> roles) {}

  private record MemberRoles(UUID userId, String nickname, String memberNumber, List<RoleCode> roles) {
    private MemberRoles(UUID userId, String nickname, String memberNumber) {
      this(userId, nickname, memberNumber, new ArrayList<>());
    }
  }
}
