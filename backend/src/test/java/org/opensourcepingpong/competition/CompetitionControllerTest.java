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

  @Test void registrationAfterDeadlineReturnsBusinessError() {
    Map<String,Object> row = openRow();
    row.put("registration_end", Timestamp.from(Instant.now().minusSeconds(60)));
    when(jdbc.queryForList(contains("for update"), eq(competition))).thenReturn(List.of(row));

    assertEquals("COMPETITION_REGISTRATION_CLOSED", assertThrows(BusinessException.class,
        () -> controller.enter(principal, competition)).code());
    verify(jdbc, never()).update(startsWith("insert into competition_entries"),
        any(org.springframework.jdbc.core.PreparedStatementSetter.class));
  }

  @Test void duplicateRegistrationReturnsBusinessError() {
    when(jdbc.queryForList(contains("for update"), eq(competition))).thenReturn(List.of(openRow()));
    when(jdbc.queryForObject(contains("count(*)"), eq(Integer.class), eq(competition))).thenReturn(0);
    doThrow(new org.springframework.dao.DuplicateKeyException("duplicate"))
        .when(jdbc).update(startsWith("insert into competition_entries"),
            any(org.springframework.jdbc.core.PreparedStatementSetter.class));

    assertEquals("COMPETITION_ALREADY_REGISTERED", assertThrows(BusinessException.class,
        () -> controller.enter(principal, competition)).code());
  }

  @Test void withdrawalAfterDeadlineReturnsBusinessError() {
    Map<String,Object> row = openRow();
    row.put("registration_end", Timestamp.from(Instant.now().minusSeconds(60)));
    when(jdbc.queryForList(startsWith("select status,registration_start"), eq(competition)))
        .thenReturn(List.of(row));

    assertEquals("COMPETITION_REGISTRATION_CLOSED", assertThrows(BusinessException.class,
        () -> controller.leave(principal, competition)).code());
    verify(jdbc, never()).update(startsWith("delete from competition_entries"), any(), any());
  }

  @Test void withdrawalBeforeDeadlineDeletesTheEntry() {
    when(jdbc.queryForList(startsWith("select status,registration_start"), eq(competition)))
        .thenReturn(List.of(openRow()));
    when(jdbc.update(startsWith("delete from competition_entries"), eq(competition), eq(user))).thenReturn(1);

    controller.leave(principal, competition);

    verify(jdbc).update(startsWith("delete from competition_entries"), eq(competition), eq(user));
  }

  @Test void updateRejectsQuotaBelowActiveEntries() {
    when(jdbc.queryForList(startsWith("select status from competitions"), eq(competition)))
        .thenReturn(List.of(Map.of("status", "REGISTRATION")));
    when(jdbc.queryForObject(startsWith("select count(*) from competition_entries"),
        eq(Integer.class), eq(competition))).thenReturn(5);
    Instant now = Instant.now();
    var request = new CompetitionController.Create("Test", null, CompetitionType.DAILY,
        CompetitionFormat.SINGLE_ELIMINATION, "PUBLIC", false, 3, 3, "Gym",
        now, now.plusSeconds(3600), now.plusSeconds(7200));

    assertEquals("COMPETITION_CAPACITY_BELOW_ENTRIES", assertThrows(BusinessException.class,
        () -> controller.update(principal, competition, request)).code());
    verify(jdbc, never()).update(startsWith("update competitions set title=?"), any(Object[].class));
  }

  @Test void approvingEntryRejectsWhenApprovedQuotaIsReached() {
    UUID entryId = UUID.randomUUID();
    when(jdbc.queryForList(contains("for update of c"), eq(entryId))).thenReturn(List.of(
        Map.of("status", "PENDING", "competition_status", "REGISTRATION",
            "competition_id", competition, "max_participants", 8)));
    when(jdbc.queryForObject(startsWith("select count(*) from competition_entries where competition_id=?"),
        eq(Integer.class), eq(competition))).thenReturn(8);

    assertEquals("COMPETITION_FULL", assertThrows(BusinessException.class,
        () -> controller.approveEntry(principal, entryId)).code());
    verify(jdbc, never()).update(startsWith("update competition_entries e set status=?"),
        any(org.springframework.jdbc.core.PreparedStatementSetter.class));
  }

  @Test void rejectingPendingEntryUpdatesRoster() {
    UUID entryId = UUID.randomUUID();
    when(jdbc.queryForList(contains("for update of c"), eq(entryId))).thenReturn(List.of(
        Map.of("status", "PENDING", "competition_status", "REGISTRATION",
            "competition_id", competition, "max_participants", 8)));
    when(jdbc.update(startsWith("update competition_entries e set status=?"),
        any(org.springframework.jdbc.core.PreparedStatementSetter.class))).thenReturn(1);

    controller.rejectEntry(principal, entryId);

    verify(jdbc).update(startsWith("update competition_entries e set status=?"),
        any(org.springframework.jdbc.core.PreparedStatementSetter.class));
  }

  private static Map<String,Object> openRow() {
    Map<String,Object> row = new HashMap<>();
    row.put("status", "REGISTRATION");
    row.put("max_participants", 8);
    row.put("registration_requires_review", false);
    row.put("registration_scope", "PUBLIC");
    row.put("registration_start", Timestamp.from(Instant.now().minusSeconds(3600)));
    row.put("registration_end", Timestamp.from(Instant.now().plusSeconds(3600)));
    return row;
  }
}
