package org.opensourcepingpong.rating;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
    when(jdbc.update(startsWith("update rating_settlements set status='COMPLETED'"), any(UUID.class))).thenReturn(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"DAILY", "EXAM"})
  void nonRatedCompetitionsFinalizeWithoutRatingLedger(String type) {
    when(jdbc.queryForList(startsWith("select competition_type"), eq(competition))).thenReturn(List.of(competition(type, "RESULT_CONFIRMING")));
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition))).thenReturn(List.of());

    service.finalizeCompetition(operator, competition);

    verify(authorization).require(operator, Permission.COMPETITION_MANAGE);
    verify(jdbc, never()).queryForList(startsWith("select m.id,m.player_a_user_id"), eq(competition));
    verify(jdbc, never()).update(startsWith("insert into rating_ledger"), any(Object[].class));
  }

  @Test
  void finalizeTenSequentialCallsOnlySettlesOnceAndRejectsTheRest() {
    AtomicBoolean settled = new AtomicBoolean();
    stubRatedCompetition();
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition)))
        .thenAnswer(inv -> settled.get() ? List.of(Map.of("status", "COMPLETED")) : List.of());
    when(jdbc.update(startsWith("update rating_settlements set status='COMPLETED'"), any(UUID.class)))
        .thenAnswer(inv -> settled.compareAndSet(false, true) ? 1 : 0);
    when(jdbc.queryForList(startsWith("select m.id,m.player_a_user_id"), eq(competition))).thenReturn(List.of());

    int rejected = 0;
    for (int i = 0; i < 10; i++) {
      try {
        service.finalizeCompetition(operator, competition);
      } catch (BusinessException e) {
        assertEquals("COMPETITION_ALREADY_FINALIZED", e.code());
        rejected++;
      }
    }

    assertEquals(9, rejected);
    verify(jdbc, times(1)).update(startsWith("insert into rating_settlements"), any(Object[].class));
    verify(jdbc, times(1)).update(startsWith("update competitions"), any(Object[].class));
    verify(jdbc, never()).update(startsWith("insert into rating_ledger"), any(Object[].class));
  }

  @Test
  void finalizeTenConcurrentCallsOnlySettlesOnceAndRejectsTheRest() throws Exception {
    AtomicBoolean settled = new AtomicBoolean();
    stubRatedCompetition();
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition)))
        .thenAnswer(inv -> settled.get() ? List.of(Map.of("status", "COMPLETED")) : List.of());
    when(jdbc.update(startsWith("update rating_settlements set status='COMPLETED'"), any(UUID.class)))
        .thenAnswer(inv -> settled.compareAndSet(false, true) ? 1 : 0);
    when(jdbc.queryForList(startsWith("select m.id,m.player_a_user_id"), eq(competition))).thenReturn(List.of());

    int threads = 10;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch startGate = new CountDownLatch(1);
    List<Future<String>> results = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      results.add(pool.submit(() -> {
        startGate.await();
        try {
          service.finalizeCompetition(operator, competition);
          return null;
        } catch (BusinessException e) {
          return e.code();
        }
      }));
    }
    startGate.countDown();

    int successes = 0;
    int rejected = 0;
    for (Future<String> future : results) {
      String code = future.get(10, TimeUnit.SECONDS);
      if (code == null) successes++;
      else if ("COMPETITION_ALREADY_FINALIZED".equals(code)) rejected++;
    }
    pool.shutdownNow();

    assertEquals(1, successes);
    assertEquals(threads - 1, rejected);
    verify(jdbc, times(1)).update(startsWith("update competitions"), any(Object[].class));
    verify(jdbc, never()).update(startsWith("insert into rating_ledger"), any(Object[].class));
  }

  @Test
  void ratedActiveMembersCreateLedgerAndUpdateCurrentRatings() {
    UUID match = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
    stubRatedCompetition();
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition))).thenReturn(List.of());
    when(jdbc.queryForList(startsWith("select m.id,m.player_a_user_id"), eq(competition))).thenReturn(List.of(match(match, a, b, a)));
    when(jdbc.queryForList(startsWith("select id from memberships"), any(UUID.class))).thenReturn(List.of(Map.of("id", UUID.randomUUID())));
    when(jdbc.queryForList(startsWith("select id from rating_ledger"), any(UUID.class))).thenReturn(List.of());
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
  void settlesMatchesInStageRoundMatchSlotOrderUsingProgressiveRatings() {
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    stubRatedCompetition();
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition))).thenReturn(List.of());
    when(jdbc.queryForList(startsWith("select m.id,m.player_a_user_id"), eq(competition)))
        .thenReturn(List.of(match(first, a, b, a, 1, 1, 1), match(second, a, b, a, 1, 2, 1)));
    when(jdbc.queryForList(startsWith("select id from memberships"), any(UUID.class))).thenReturn(List.of(Map.of("id", UUID.randomUUID())));
    when(jdbc.queryForList(startsWith("select id from rating_ledger"), any(UUID.class))).thenReturn(List.of());
    Map<UUID, Integer> ratings = new HashMap<>(Map.of(a, 1400, b, 1400));
    when(jdbc.queryForList(startsWith("select user_id,current_rating"), any(UUID.class), any(UUID.class)))
        .thenAnswer(inv -> List.of(account(a, ratings.get(a)), account(b, ratings.get(b))));
    when(jdbc.update(startsWith("update rating_accounts"), anyInt(), anyInt(), anyInt(), anyInt(), any(UUID.class)))
        .thenAnswer(inv -> { ratings.put(inv.getArgument(5, UUID.class), inv.getArgument(1, Integer.class)); return 1; });
    List<Object[]> ledgerRows = new ArrayList<>();
    when(jdbc.update(startsWith("insert into rating_ledger"), any(Object[].class)))
        .thenAnswer(inv -> { ledgerRows.add(inv.getArguments()); return 1; });

    service.finalizeCompetition(operator, competition);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, atLeastOnce()).queryForList(sql.capture(), eq(competition));
    String matchQuery = sql.getAllValues().stream().filter(s -> s.contains("from matches")).findFirst().orElseThrow();
    assertTrue(matchQuery.contains("order by s.stage_order asc,m.round_number asc,m.match_number asc"));

    assertEquals(4, ledgerRows.size());
    assertEquals(1400, ledgerRows.get(0)[8]); // A 首场赛前
    assertEquals(1408, ledgerRows.get(0)[10]); // A 首场赛后
    assertEquals(1392, ledgerRows.get(1)[10]); // B 首场赛后
    assertEquals(1408, ledgerRows.get(2)[8]); // A 次场赛前 = 首场结算后（逐场即时积分）
    assertEquals(1392, ledgerRows.get(3)[8]); // B 次场赛前
    assertEquals(1415, ledgerRows.get(2)[10]); // 差 16 → 高分者胜 +7
    assertEquals(1385, ledgerRows.get(3)[10]);
    assertEquals(1, ledgerRows.get(0)[14]);    // stage_order
    assertEquals(1, ledgerRows.get(0)[15]);    // round_number
    assertEquals(1, ledgerRows.get(0)[16]);    // match_number（首场）
    assertEquals(2, ledgerRows.get(2)[15]);    // 次场 round_number
  }

  @Test
  void writesStageRoundMatchSlotsIntoLedgerRows() {
    UUID match = UUID.randomUUID();
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    stubRatedCompetition();
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition))).thenReturn(List.of());
    when(jdbc.queryForList(startsWith("select m.id,m.player_a_user_id"), eq(competition)))
        .thenReturn(List.of(match(match, a, b, a, 2, 3, 7)));
    when(jdbc.queryForList(startsWith("select id from memberships"), any(UUID.class))).thenReturn(List.of(Map.of("id", UUID.randomUUID())));
    when(jdbc.queryForList(startsWith("select id from rating_ledger"), any(UUID.class))).thenReturn(List.of());
    when(jdbc.queryForList(startsWith("select user_id,current_rating"), any(UUID.class), any(UUID.class)))
        .thenReturn(List.of(account(a, 1400), account(b, 1400)));
    List<Object[]> ledgerRows = new ArrayList<>();
    when(jdbc.update(startsWith("insert into rating_ledger"), any(Object[].class)))
        .thenAnswer(inv -> { ledgerRows.add(inv.getArguments()); return 1; });

    service.finalizeCompetition(operator, competition);

    assertEquals(2, ledgerRows.size());
    for (Object[] row : ledgerRows) {
      assertEquals(2, row[14]); // stage_order
      assertEquals(3, row[15]); // round_number
      assertEquals(7, row[16]); // match_number
    }
    assertEquals("WIN", ledgerRows.get(0)[11]);
    assertEquals("LOSS", ledgerRows.get(1)[11]);
    assertEquals(a, ledgerRows.get(0)[2]);
    assertEquals(b, ledgerRows.get(1)[2]);
  }

  @Test
  void finalizingNotStartedRatedCompetitionWritesNoLedgerAndFinishes() {
    stubRatedCompetition();
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition))).thenReturn(List.of());
    when(jdbc.queryForList(startsWith("select m.id,m.player_a_user_id"), eq(competition))).thenReturn(List.of());

    service.finalizeCompetition(operator, competition);

    verify(jdbc, never()).update(startsWith("insert into rating_ledger"), any(Object[].class));
    verify(jdbc, times(1)).update(startsWith("insert into rating_settlements"), any(Object[].class));
    verify(jdbc).update(startsWith("update rating_settlements set status='COMPLETED'"), any(UUID.class));
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
    stubRatedCompetition();
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition)))
        .thenReturn(List.of());
    when(jdbc.queryForList(startsWith("select m.id,m.player_a_user_id"), eq(competition)))
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
    stubRatedCompetition();
    when(jdbc.queryForList(startsWith("select status from rating_settlements"), eq(competition)))
        .thenReturn(List.of());
    when(jdbc.queryForList(startsWith("select m.id,m.player_a_user_id"), eq(competition)))
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

  private void stubRatedCompetition() {
    when(jdbc.queryForList(startsWith("select competition_type"), eq(competition))).thenReturn(List.of(competition("RATED", "RESULT_CONFIRMING")));
  }

  private static Map<String, Object> competition(String type, String status) {
    return competition(type, status, "PING_PONG");
  }

  private static Map<String, Object> competition(String type, String status, String sportCode) {
    return new HashMap<>(Map.of("competition_type", type, "status", status, "sport_code", sportCode));
  }

  private static Map<String, Object> match(UUID id, UUID a, UUID b, UUID winner) {
    return match(id, a, b, winner, 1, 1, 1);
  }

  private static Map<String, Object> match(UUID id, UUID a, UUID b, UUID winner, int stageOrder, int roundNumber, int matchNumber) {
    Map<String, Object> row = new HashMap<>();
    row.put("id", id);
    row.put("player_a_user_id", a);
    row.put("player_b_user_id", b);
    row.put("winner_user_id", winner);
    row.put("loser_user_id", winner.equals(a) ? b : a);
    row.put("stage_order", stageOrder);
    row.put("round_number", roundNumber);
    row.put("match_number", matchNumber);
    return row;
  }

  private static Map<String, Object> account(UUID user, int rating) {
    return new HashMap<>(Map.of("user_id", user, "current_rating", rating));
  }
}
