package org.opensourcepingpong.competition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class MatchControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final ScheduleService schedules = mock(ScheduleService.class);
  private final UUID operator = UUID.randomUUID();
  private final AuthPrincipal principal = new AuthPrincipal(operator, "manager");
  private MatchController controller;

  @BeforeEach
  void setUp() {
    controller = new MatchController(jdbc, authorization, schedules);
  }

  @Test
  void startRequiresReadyMatchWithBothPlayers() {
    UUID match = UUID.randomUUID();
    when(jdbc.update(anyString(), any(), eq(match))).thenReturn(1);

    controller.start(principal, match);

    verify(authorization).require(operator, Permission.MATCH_SCORE_EDIT);
  }

  @Test
  void startRejectsInvalidState() {
    UUID match = UUID.randomUUID();
    when(jdbc.update(anyString(), any(), eq(match))).thenReturn(0);

    BusinessException ex = assertThrows(BusinessException.class, () -> controller.start(principal, match));

    assertEquals("MATCH_INVALID_STATE", ex.code());
  }

  @Test
  void scoreCompletesPlayingMatchAndAdvancesWinner() {
    UUID match = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select player_a_user_id"), eq(match))).thenReturn(List.of(matchRow(a, b, "PLAYING", 3, 2)));
    when(jdbc.update(startsWith("update matches m set status='COMPLETED'"), any(), any(), anyInt(), anyInt(), any(), eq(match), eq(2L))).thenReturn(1);
    when(jdbc.update(startsWith("delete from match_games"), eq(match))).thenReturn(1);
    when(jdbc.update(startsWith("insert into match_games"), any(), eq(match), anyInt(), anyInt(), anyInt(), any())).thenReturn(1);
    controller.score(principal, match, new MatchController.ScoreRequest(2, List.of(
        new MatchController.Game(11, 8),
        new MatchController.Game(11, 9))));

    verify(authorization).require(operator, Permission.MATCH_SCORE_EDIT);
    verify(schedules).advance(match, a);
  }

  @Test
  void scoreRejectsOldVersion() {
    UUID match = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select player_a_user_id"), eq(match))).thenReturn(List.of(matchRow(UUID.randomUUID(), UUID.randomUUID(), "PLAYING", 3, 3)));

    BusinessException ex = assertThrows(BusinessException.class, () -> controller.score(principal, match,
        new MatchController.ScoreRequest(2, List.of(new MatchController.Game(11, 8), new MatchController.Game(11, 8)))));

    assertEquals("MATCH_VERSION_CONFLICT", ex.code());
    assertEquals(HttpStatus.CONFLICT, ex.status());
    verify(schedules, never()).advance(any(), any());
  }

  @Test
  void scoreRejectsNonPlayingMatchAndInvalidScore() {
    UUID match = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select player_a_user_id"), eq(match))).thenReturn(List.of(matchRow(UUID.randomUUID(), UUID.randomUUID(), "READY", 3, 1)));
    assertEquals("MATCH_INVALID_STATE", assertThrows(BusinessException.class, () -> controller.score(principal, match,
        new MatchController.ScoreRequest(1, List.of(new MatchController.Game(11, 8), new MatchController.Game(11, 8))))).code());

    when(jdbc.queryForList(startsWith("select player_a_user_id"), eq(match))).thenReturn(List.of(matchRow(UUID.randomUUID(), UUID.randomUUID(), "PLAYING", 3, 1)));
    assertEquals("MATCH_INVALID_SCORE", assertThrows(BusinessException.class, () -> controller.score(principal, match,
        new MatchController.ScoreRequest(1, List.of(new MatchController.Game(11, 10), new MatchController.Game(11, 8))))).code());
  }

  private static Map<String, Object> matchRow(UUID a, UUID b, String status, int bestOf, long version) {
    Map<String, Object> row = new HashMap<>();
    row.put("player_a_user_id", a);
    row.put("player_b_user_id", b);
    row.put("best_of", bestOf);
    row.put("status", status);
    row.put("version", version);
    return row;
  }
}
