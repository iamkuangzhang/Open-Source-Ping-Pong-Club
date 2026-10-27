package org.opensourcepingpong.sport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.springframework.jdbc.core.JdbcTemplate;

class SportControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final SportController controller = new SportController(jdbc, authorization);
  private final UUID userId = UUID.randomUUID();

  @Test
  void sportCreationRequiresSportManagementPermission() {
    doThrow(new BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "denied"))
        .when(authorization).require(userId, Permission.SPORTS_MANAGE);

    BusinessException exception = assertThrows(BusinessException.class, () ->
        controller.create(new AuthPrincipal(userId, "admin"), new SportController.CreateSport("VOLLEYBALL", "排球")));

    assertEquals("PERMISSION_DENIED", exception.code());
    verify(jdbc, org.mockito.Mockito.never()).update(anyString(), any(Object[].class));
  }

  @Test
  void sportCreationNormalizesCodeAndPersistsAuditRecord() {
    when(jdbc.queryForList("select id from sports where code=?", "VOLLEYBALL")).thenReturn(List.of());
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    var response = controller.create(
        new AuthPrincipal(userId, "admin"),
        new SportController.CreateSport("volleyball", "排球"));

    assertEquals("VOLLEYBALL", ((Map<?, ?>) response.data()).get("code"));
    verify(authorization).require(userId, Permission.SPORTS_MANAGE);
    verify(jdbc).queryForList(eq("select id from sports where code=?"), eq("VOLLEYBALL"));
    verify(jdbc, org.mockito.Mockito.times(2)).update(anyString(), any(Object[].class));
  }
}
