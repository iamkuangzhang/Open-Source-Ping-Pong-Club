package org.opensourcepingpong.competition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.springframework.jdbc.core.JdbcTemplate;

class ScheduleServiceTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final UUID manager = UUID.randomUUID();
  private final UUID competition = UUID.randomUUID();
  private ScheduleService service;

  @BeforeEach
  void setUp() {
    service = new ScheduleService(jdbc, authorization);
  }

  @Test
  void arrangeRequiresCompetitionManagePermission() {
    doThrow(new BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "denied"))
        .when(authorization).require(manager, Permission.COMPETITION_MANAGE);

    BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
        BusinessException.class, () -> service.arrange(manager, competition, 1));

    assertEquals("PERMISSION_DENIED", exception.code());
    verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
  }

  @Test
  void arrangeReturnsNotFoundForUnknownCompetition() {
    when(jdbc.queryForList(startsWith("select format,best_of,status"), eq(competition))).thenReturn(List.of());

    BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
        BusinessException.class, () -> service.arrange(manager, competition, 1));

    assertEquals("COMPETITION_NOT_FOUND", exception.code());
  }

  @Test
  void arrangeRequiresArrangingStateAndAtLeastTwoApprovedParticipants() {
    when(jdbc.queryForList(startsWith("select format,best_of,status"), eq(competition)))
        .thenReturn(List.of(competitionRow("SINGLE_ELIMINATION", "REGISTRATION")));
    BusinessException wrongState = org.junit.jupiter.api.Assertions.assertThrows(
        BusinessException.class, () -> service.arrange(manager, competition, 1));
    assertEquals("COMPETITION_INVALID_STATE", wrongState.code());

    when(jdbc.queryForList(startsWith("select format,best_of,status"), eq(competition)))
        .thenReturn(List.of(competitionRow("SINGLE_ELIMINATION", "ARRANGING")));
    when(jdbc.queryForList(startsWith("select user_id from competition_entries"), eq(UUID.class), eq(competition)))
        .thenReturn(List.of(UUID.randomUUID()));
    BusinessException tooFewParticipants = org.junit.jupiter.api.Assertions.assertThrows(
        BusinessException.class, () -> service.arrange(manager, competition, 1));
    assertEquals("COMPETITION_INSUFFICIENT_PARTICIPANTS", tooFewParticipants.code());
  }

  @Test
  void knockoutArrangementCreatesMatchesAndMovesCompetitionToReady() {
    List<UUID> players = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    when(jdbc.queryForList(startsWith("select format,best_of,status"), eq(competition)))
        .thenReturn(List.of(competitionRow("SINGLE_ELIMINATION", "ARRANGING")));
    when(jdbc.queryForList(startsWith("select user_id from competition_entries"), eq(UUID.class), eq(competition)))
        .thenReturn(players);

    service.arrange(manager, competition, 2026L);

    verify(jdbc).update(startsWith("insert into competition_stages"), any(Object[].class));
    verify(jdbc, times(3)).update(startsWith("insert into matches"), any(Object[].class));
    verify(jdbc).update(startsWith("update competitions set status='READY'"), eq(2026L), any(), eq(competition));
  }

  @Test
  void oddKnockoutFieldAutomaticallyCompletesByeMatches() {
    List<UUID> players = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    when(jdbc.queryForList(startsWith("select format,best_of,status"), eq(competition)))
        .thenReturn(List.of(competitionRow("SINGLE_ELIMINATION", "ARRANGING")));
    when(jdbc.queryForList(startsWith("select user_id from competition_entries"), eq(UUID.class), eq(competition)))
        .thenReturn(players);
    Map<String, Object> finalMatch = new HashMap<>();
    finalMatch.put("next_match_id", null);
    finalMatch.put("winner_to_slot", "A");
    when(jdbc.queryForList(startsWith("select next_match_id,winner_to_slot"), any(UUID.class)))
        .thenReturn(List.of(finalMatch));

    service.arrange(manager, competition, 7L);

    verify(jdbc, times(1)).update(startsWith("update matches set status='COMPLETED'"), any(Object[].class));
    verify(jdbc).update(startsWith("update competitions set status='READY'"), eq(7L), any(), eq(competition));
  }

  @Test
  void roundRobinCreatesEveryPairOnce() {
    List<UUID> players = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    when(jdbc.queryForList(startsWith("select format,best_of,status"), eq(competition)))
        .thenReturn(List.of(competitionRow("ROUND_ROBIN", "ARRANGING")));
    when(jdbc.queryForList(startsWith("select user_id from competition_entries"), eq(UUID.class), eq(competition)))
        .thenReturn(players);

    service.arrange(manager, competition, 9L);

    verify(jdbc, times(6)).update(startsWith("insert into matches"), any(Object[].class));
    verify(jdbc, times(4)).update(startsWith("insert into competition_group_members"), any(Object[].class));
  }

  @Test
  void confirmingRequiresAllMatchesCompleted() {
    when(jdbc.queryForObject(startsWith("select count(*)"), eq(Integer.class), eq(competition))).thenReturn(1);

    BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
        BusinessException.class, () -> service.confirming(manager, competition));

    assertEquals("COMPETITION_MATCHES_INCOMPLETE", exception.code());
    verify(authorization).require(manager, Permission.COMPETITION_MANAGE);
    verify(jdbc, never()).queryForList(startsWith("select status from competitions"), any(Object[].class));
  }

  @Test
  void completedMatchesAllowTransitionToResultConfirmation() {
    when(jdbc.queryForObject(startsWith("select count(*)"), eq(Integer.class), eq(competition))).thenReturn(0);
    when(jdbc.queryForList(startsWith("select status from competitions"), eq(competition)))
        .thenReturn(List.of(Map.of("status", "RUNNING")));

    service.confirming(manager, competition);

    verify(jdbc).update(startsWith("update competitions set status=?"), eq("RESULT_CONFIRMING"), eq(competition));
  }

  private static Map<String, Object> competitionRow(String format, String status) {
    return Map.of("format", format, "best_of", 3, "status", status);
  }
}
