package org.opensourcepingpong.sport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.springframework.dao.DuplicateKeyException;
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

  @Test
  void duplicateSportCodeIsRejectedBeforeInsert() {
    when(jdbc.queryForList("select id from sports where code=?", "VOLLEYBALL"))
        .thenReturn(List.of(Map.of("id", UUID.randomUUID())));

    BusinessException exception = assertThrows(BusinessException.class, () ->
        controller.create(new AuthPrincipal(userId, "admin"), new SportController.CreateSport("volleyball", "排球")));

    assertEquals("SPORT_ALREADY_EXISTS", exception.code());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void concurrentDuplicateInsertIsMappedToConflict() {
    when(jdbc.queryForList("select id from sports where code=?", "VOLLEYBALL")).thenReturn(List.of());
    when(jdbc.update(startsWith("insert into sports"), any(Object[].class)))
        .thenThrow(new DuplicateKeyException("concurrent insert"));

    BusinessException exception = assertThrows(BusinessException.class, () ->
        controller.create(new AuthPrincipal(userId, "admin"), new SportController.CreateSport("volleyball", "排球")));

    assertEquals("SPORT_ALREADY_EXISTS", exception.code());
    verify(jdbc, never()).update(startsWith("insert into audit_logs"), any(Object[].class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1BAD", "_BAD", "A", "A-B", "BAD CODE", "包含中文"})
  void invalidSportCodesFailBeanValidationPattern(String invalidCode) {
    try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
      var violations = factory.getValidator().validate(new SportController.CreateSport(invalidCode, "排球"));
      assertFalse(violations.isEmpty());
    }
  }

  @Test
  void sportNameMustBeNonBlankAndAtMostFortyCharacters() {
    try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertFalse(validator.validate(new SportController.CreateSport("VOLLEYBALL", " ")).isEmpty());
      assertFalse(validator.validate(new SportController.CreateSport("VOLLEYBALL", "球".repeat(41))).isEmpty());
      assertEquals(0, validator.validate(new SportController.CreateSport("VOLLEYBALL", "排球")).size());
    }
  }

  @Test
  void publicSportListOnlyReturnsActiveTypesInConfiguredOrder() {
    when(jdbc.queryForList("select id,code,name from sports where active=true order by sort_order,name"))
        .thenReturn(List.of(Map.of("code", "PING_PONG", "name", "乒乓球")));

    var response = controller.list();

    assertEquals(1, ((List<?>) response.data()).size());
    verify(jdbc).queryForList("select id,code,name from sports where active=true order by sort_order,name");
  }

  @Test
  void sportUpdateRequiresSportManagementPermission() {
    doThrow(new BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "denied"))
        .when(authorization).require(userId, Permission.SPORTS_MANAGE);

    BusinessException exception = assertThrows(BusinessException.class, () ->
        controller.update(new AuthPrincipal(userId, "admin"), UUID.randomUUID(),
            new SportController.UpdateSport("排球", 5)));

    assertEquals("PERMISSION_DENIED", exception.code());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void sportUpdateRefreshesUpdatedAtAndWritesAudit() {
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    controller.update(new AuthPrincipal(userId, "admin"), UUID.randomUUID(),
        new SportController.UpdateSport("  排球  ", 5));

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc, org.mockito.Mockito.times(2)).update(sql.capture(), params.capture());
    String updateSql = sql.getAllValues().get(0);
    assertTrue(updateSql.contains("update sports"));
    assertTrue(updateSql.contains("sort_order=?"));
    assertTrue(updateSql.contains("updated_at=?"));
    String auditSql = sql.getAllValues().get(1);
    assertTrue(auditSql.contains("insert into audit_logs"));
    assertTrue(java.util.Arrays.asList(params.getAllValues().get(1)).contains("SPORT_UPDATED"));
  }

  @Test
  void sportUpdateReturnsNotFoundWhenSportMissing() {
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

    BusinessException exception = assertThrows(BusinessException.class, () ->
        controller.update(new AuthPrincipal(userId, "admin"), UUID.randomUUID(),
            new SportController.UpdateSport("排球", 5)));

    assertEquals("SPORT_NOT_FOUND", exception.code());
  }

  @Test
  void sportStatusChangeRequiresSportManagementPermission() {
    doThrow(new BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "denied"))
        .when(authorization).require(userId, Permission.SPORTS_MANAGE);

    BusinessException exception = assertThrows(BusinessException.class, () ->
        controller.updateStatus(new AuthPrincipal(userId, "admin"), UUID.randomUUID(),
            new SportController.SportStatus(false)));

    assertEquals("PERMISSION_DENIED", exception.code());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void sportStatusChangePersistsActiveFlagWithUpdatedAtAndAudits() {
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    controller.updateStatus(new AuthPrincipal(userId, "admin"), UUID.randomUUID(),
        new SportController.SportStatus(false));

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc, org.mockito.Mockito.times(2)).update(sql.capture(), params.capture());
    String statusSql = sql.getAllValues().get(0);
    assertTrue(statusSql.contains("active=?"));
    assertTrue(statusSql.contains("updated_at=?"));
    assertTrue(java.util.Arrays.asList(params.getAllValues().get(1)).contains("SPORT_DISABLED"));
  }

  @Test
  void sportStatusChangeReturnsNotFoundWhenSportMissing() {
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

    BusinessException exception = assertThrows(BusinessException.class, () ->
        controller.updateStatus(new AuthPrincipal(userId, "admin"), UUID.randomUUID(),
            new SportController.SportStatus(true)));

    assertEquals("SPORT_NOT_FOUND", exception.code());
  }
}
