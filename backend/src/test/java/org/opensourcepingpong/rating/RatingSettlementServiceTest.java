package org.opensourcepingpong.rating;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.springframework.jdbc.core.JdbcTemplate;

class RatingSettlementServiceTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final UUID operator = UUID.randomUUID();
  private final UUID competition = UUID.randomUUID();
  private RatingSettlementService service;

  @BeforeEach
  void setUp() {
    service = new RatingSettlementService(jdbc, authorization);
  }

  @ParameterizedTest
  @ValueSource(strings = {"DAILY", "EXAM"})
  void nonRatedCompetitionsFinalizeWithoutRatingLedger(String type) {
    when(jdbc.queryForList(startsWith("select competition_type"), eq(competition))).thenReturn(List.of(competition(type, "RESULT_CONFIRMING")));
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition))).thenReturn(List.of());

    service.finalizeCompetition(operator, competition);

    verify(authorization).require(operator, Permission.COMPETITION_MANAGE);
    verify(jdbc, never()).queryForList(startsWith("select id,player_a_user_id"), eq(competition));
    verify(jdbc, never()).update(startsWith("insert into rating_ledger"), any(Object[].class));
  }

  @Test
  void finalizedCompetitionIsIdempotentEvenWhenCalledRepeatedly() {
    when(jdbc.queryForList(startsWith("select competition_type"), eq(competition))).thenReturn(List.of(competition("RATED", "FINISHED")));
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition))).thenReturn(List.of(Map.of("status", "COMPLETED")));

    for (int i = 0; i < 10; i++) service.finalizeCompetition(operator, competition);

    verify(jdbc, never()).update(startsWith("insert into rating_settlements"), any(Object[].class));
    verify(jdbc, never()).update(startsWith("insert into rating_ledger"), any(Object[].class));
  }

  @Test
  void ratedActiveMembersCreateLedgerAndUpdateCurrentRatings() {
    UUID match = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select competition_type"), eq(competition))).thenReturn(List.of(competition("RATED", "RESULT_CONFIRMING")));
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition))).thenReturn(List.of());
    when(jdbc.queryForList(startsWith("select id,player_a_user_id"), eq(competition))).thenReturn(List.of(match(match, a, b, a)));
    when(jdbc.queryForList(startsWith("select id from memberships"), any(UUID.class))).thenReturn(List.of(Map.of("id", UUID.randomUUID())));
    when(jdbc.queryForList(startsWith("select id from rating_ledger"), eq(match))).thenReturn(List.of());
    when(jdbc.queryForList(startsWith("select user_id,current_rating"), any(UUID.class), any(UUID.class))).thenReturn(List.of(
        account(a, 1400),
        account(b, 1400)));

    service.finalizeCompetition(operator, competition);

    verify(jdbc, times(2)).update(startsWith("update rating_accounts"), anyInt(), anyInt(), anyInt(), anyInt(), any());
    verify(jdbc, times(2)).update(startsWith("insert into rating_ledger"), any(Object[].class));
    verify(jdbc).update(startsWith("update matches set rating_eligible=true"), eq(match));
    verify(jdbc).update(startsWith("update competitions"), eq("FINISHED"), eq(competition));
  }

  @Test
  void wrongCompetitionStateCannotFinalize() {
    when(jdbc.queryForList(startsWith("select competition_type"), eq(competition))).thenReturn(List.of(competition("RATED", "RUNNING")));

    var ex = assertThrows(BusinessException.class, () -> service.finalizeCompetition(operator, competition));

    assertEquals("COMPETITION_INVALID_STATE", ex.code());
  }

  @Test
  void otherSportsCannotSettleSharedRating() {
    when(jdbc.queryForList(startsWith("select competition_type"), eq(competition)))
        .thenReturn(List.of(competition("RATED", "RESULT_CONFIRMING", "BADMINTON")));
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition))).thenReturn(List.of());

    var exception = assertThrows(BusinessException.class, () -> service.finalizeCompetition(operator, competition));

    assertEquals("SPORT_RATING_UNSUPPORTED", exception.code());
    verify(jdbc, never()).update(startsWith("insert into rating_settlements"), any(Object[].class));
  }

  @Test
  void inactiveMembersAndAlreadySettledMatchesDoNotReceiveDuplicateLedgerEntries() {
    UUID skippedMatch = UUID.randomUUID();
    UUID settledMatch = UUID.randomUUID();
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    UUID d = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select competition_type"), eq(competition)))
        .thenReturn(List.of(competition("RATED", "RESULT_CONFIRMING")));
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition)))
        .thenReturn(List.of());
    when(jdbc.queryForList(startsWith("select id,player_a_user_id"), eq(competition)))
        .thenReturn(List.of(match(skippedMatch, a, b, a), match(settledMatch, c, d, d)));
    when(jdbc.queryForList(startsWith("select id from memberships"), eq(a))).thenReturn(List.of());
    when(jdbc.queryForList(startsWith("select id from memberships"), eq(b)))
        .thenReturn(List.of(Map.of("id", UUID.randomUUID())));
    when(jdbc.queryForList(startsWith("select id from memberships"), eq(c)))
        .thenReturn(List.of(Map.of("id", UUID.randomUUID())));
    when(jdbc.queryForList(startsWith("select id from memberships"), eq(d)))
        .thenReturn(List.of(Map.of("id", UUID.randomUUID())));
    when(jdbc.queryForList(startsWith("select id from rating_ledger"), eq(settledMatch)))
        .thenReturn(List.of(Map.of("id", UUID.randomUUID())));

    service.finalizeCompetition(operator, competition);

    verify(jdbc, never()).queryForList(startsWith("select user_id,current_rating"), any(UUID.class), any(UUID.class));
    verify(jdbc, never()).update(startsWith("insert into rating_ledger"), any(Object[].class));
  }

  @Test
  void missingRatingAccountFailsSettlementWithoutMarkingCompetitionFinished() {
    UUID matchId = UUID.randomUUID();
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select competition_type"), eq(competition)))
        .thenReturn(List.of(competition("RATED", "RESULT_CONFIRMING")));
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition)))
        .thenReturn(List.of());
    when(jdbc.queryForList(startsWith("select id,player_a_user_id"), eq(competition)))
        .thenReturn(List.of(match(matchId, a, b, a)));
    when(jdbc.queryForList(startsWith("select id from memberships"), any(UUID.class)))
        .thenReturn(List.of(Map.of("id", UUID.randomUUID())));
    when(jdbc.queryForList(startsWith("select id from rating_ledger"), eq(matchId))).thenReturn(List.of());
    when(jdbc.queryForList(startsWith("select user_id,current_rating"), any(UUID.class), any(UUID.class)))
        .thenReturn(List.of());

    BusinessException exception = assertThrows(
        BusinessException.class, () -> service.finalizeCompetition(operator, competition));

    assertEquals("RATING_ACCOUNT_NOT_FOUND", exception.code());
    verify(jdbc, never()).update(startsWith("update competitions"), any(Object[].class));
  }

  private static Map<String, Object> competition(String type, String status) {
    return competition(type, status, "PING_PONG");
  }

  private static Map<String, Object> competition(String type, String status, String sportCode) {
    return new HashMap<>(Map.of("competition_type", type, "status", status, "sport_code", sportCode));
  }

  private static Map<String, Object> match(UUID id, UUID a, UUID b, UUID winner) {
    Map<String, Object> row = new HashMap<>();
    row.put("id", id);
    row.put("player_a_user_id", a);
    row.put("player_b_user_id", b);
    row.put("winner_user_id", winner);
    row.put("loser_user_id", winner.equals(a) ? b : a);
    return row;
  }

  private static Map<String, Object> account(UUID user, int rating) {
    return new HashMap<>(Map.of("user_id", user, "current_rating", rating));
  }
}
