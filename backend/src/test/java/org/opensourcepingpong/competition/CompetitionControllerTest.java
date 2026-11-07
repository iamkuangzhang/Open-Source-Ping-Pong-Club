package org.opensourcepingpong.competition;

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
import org.opensourcepingpong.rating.RatingSettlementService;
import org.springframework.jdbc.core.JdbcTemplate;

class CompetitionControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AuthorizationService auth = mock(AuthorizationService.class);
  private final ScheduleService schedules = mock(ScheduleService.class);
  private final RatingSettlementService ratings = mock(RatingSettlementService.class);
  private final UUID user = UUID.randomUUID(), competition = UUID.randomUUID();
  private final AuthPrincipal principal = new AuthPrincipal(user, "test");
  private CompetitionController controller;

  @BeforeEach void setup() { controller = new CompetitionController(jdbc, auth, schedules, ratings); }

  @Test void rejectsInvalidCompetitionTimesBeforeInsert() {
    Instant now = Instant.now();
    var request = new CompetitionController.Create("Test", null, CompetitionType.RATED,
        CompetitionFormat.SINGLE_ELIMINATION, "PUBLIC", false, 8, 5, "Gym",
        now, now.plusSeconds(7200), now.plusSeconds(3600));
    assertEquals("VALIDATION_ERROR", assertThrows(BusinessException.class,
        () -> controller.create(principal, request)).code());
    verify(jdbc, never()).update(startsWith("insert into competitions"), any(org.springframework.jdbc.core.PreparedStatementSetter.class));
  }

  @Test void closeRequiresTwoApprovedEntries() {
    when(jdbc.queryForList(contains("for update"), eq(competition)))
        .thenReturn(List.of(Map.of("status", "REGISTRATION")));
    when(jdbc.queryForObject(contains("count(*)"), eq(Integer.class), eq(competition))).thenReturn(1);
    assertEquals("COMPETITION_INSUFFICIENT_PARTICIPANTS", assertThrows(BusinessException.class,
        () -> controller.close(principal, competition)).code());
    verify(jdbc, never()).update(startsWith("update competitions set status='ARRANGING'"), any(), any());
  }

  @Test void fullCompetitionRejectsRegistration() {
    Map<String,Object> row = new HashMap<>();
    row.put("status", "REGISTRATION"); row.put("max_participants", 2);
    row.put("registration_requires_review", false); row.put("registration_scope", "PUBLIC");
    row.put("registration_start", Timestamp.from(Instant.now().minusSeconds(3600)));
    row.put("registration_end", Timestamp.from(Instant.now().plusSeconds(3600)));
    when(jdbc.queryForList(contains("for update"), eq(competition))).thenReturn(List.of(row));
    when(jdbc.queryForObject(contains("count(*)"), eq(Integer.class), eq(competition))).thenReturn(2);
    assertEquals("COMPETITION_FULL", assertThrows(BusinessException.class,
        () -> controller.enter(principal, competition)).code());
  }

  @Test void managerPermissionIsRequiredForClosing() {
    doThrow(new BusinessException(org.springframework.http.HttpStatus.FORBIDDEN,"PERMISSION_DENIED","forbidden"))
        .when(auth).require(user, Permission.COMPETITION_MANAGE);
    assertEquals("PERMISSION_DENIED", assertThrows(BusinessException.class,
        () -> controller.close(principal, competition)).code());
    verifyNoInteractions(jdbc);
  }
}
