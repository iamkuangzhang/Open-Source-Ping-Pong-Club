package org.opensourcepingpong.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class OrganizationControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final OrganizationController controller = new OrganizationController(jdbc, authorization);
  private final UUID userId = UUID.randomUUID();
  private final UUID roleId = UUID.randomUUID();

  @Test
  void roleGrantRequiresRoleManagePermission() {
    doThrow(new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "denied"))
        .when(authorization).require(userId, Permission.ROLE_MANAGE);

    BusinessException exception = assertThrows(BusinessException.class, () ->
        controller.updateRole(new AuthPrincipal(userId, "admin"), UUID.randomUUID(),
            new OrganizationController.RoleUpdate(RoleCode.ADMIN, true)));

    assertEquals("PERMISSION_DENIED", exception.code());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void roleGrantInsertsWithoutExplicitPrimaryKeyAndRevivesRevokedRole() {
    List<Map<String, Object>> membershipRows = List.of(Map.of());
    when(jdbc.queryForList("select id from memberships where user_id=? and status='ACTIVE'", userId))
        .thenReturn(membershipRows);
    List<Map<String, Object>> roleRows = List.of(Map.of("id", roleId));
    when(jdbc.queryForList("select id from organization_roles where code=?", RoleCode.ADMIN.name()))
        .thenReturn(roleRows);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    when(authorization.roles(userId)).thenReturn(Set.of(RoleCode.ADMIN));

    var response = controller.updateRole(
        new AuthPrincipal(userId, "admin"), userId,
        new OrganizationController.RoleUpdate(RoleCode.ADMIN, true));

    assertEquals(Set.of(RoleCode.ADMIN), response.data());
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, times(2)).update(sql.capture(), any(Object[].class));
    String grantSql = sql.getAllValues().get(0);
    assertTrue(grantSql.contains(
        "insert into user_organization_roles(user_id,role_id,granted_by,granted_at)"));
    assertFalse(grantSql.contains("id,user_id"));
    assertTrue(grantSql.contains("on conflict(user_id,role_id) do update"));
    assertTrue(sql.getAllValues().get(1).contains("insert into audit_logs"));
  }

  @Test
  void roleRevokeUpdatesRevocationFields() {
    List<Map<String, Object>> membershipRows = List.of(Map.of());
    when(jdbc.queryForList("select id from memberships where user_id=? and status='ACTIVE'", userId))
        .thenReturn(membershipRows);
    List<Map<String, Object>> roleRows = List.of(Map.of("id", roleId));
    when(jdbc.queryForList("select id from organization_roles where code=?", RoleCode.ADMIN.name()))
        .thenReturn(roleRows);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    when(authorization.roles(userId)).thenReturn(Set.of());

    controller.updateRole(
        new AuthPrincipal(userId, "admin"), userId,
        new OrganizationController.RoleUpdate(RoleCode.ADMIN, false));

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, times(2)).update(sql.capture(), any(Object[].class));
    assertTrue(sql.getAllValues().get(0).contains("set revoked_by=?,revoked_at=?"));
  }

  @Test
  void roleGrantRejectsInactiveMember() {
    when(jdbc.queryForList("select id from memberships where user_id=? and status='ACTIVE'", userId))
        .thenReturn(List.<Map<String, Object>>of());

    BusinessException exception = assertThrows(BusinessException.class, () ->
        controller.updateRole(new AuthPrincipal(userId, "admin"), userId,
            new OrganizationController.RoleUpdate(RoleCode.ADMIN, true)));

    assertEquals("MEMBER_NOT_FOUND", exception.code());
  }
}
