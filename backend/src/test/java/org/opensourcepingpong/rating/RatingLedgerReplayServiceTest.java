package org.opensourcepingpong.rating;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RatingLedgerReplayServiceTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final UUID user = UUID.randomUUID();
  private final UUID opponent = UUID.randomUUID();
  private RatingLedgerReplayService service;

  @BeforeEach
  void setUp() {
    service = new RatingLedgerReplayService(jdbc);
  }

  @Test
  void replaysInitialAndMatchLedgerToExpectedCurrentRating() {
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select l.id,l.source_type"), eq(user))).thenReturn(List.of(
        ledgerRow("INITIAL", null, null, 1400, 1400, null),
        ledgerRow("MATCH", first, "WIN", 8, 1408, 1400),
        ledgerRow("MATCH", second, "LOSS", -10, 1398, 1392)));

    assertEquals(1398, service.rebuildRating(user));
  }

  @Test
  void replaysOpponentLedgerToExpectedCurrentRating() {
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select l.id,l.source_type"), eq(opponent))).thenReturn(List.of(
        ledgerRow("INITIAL", null, null, 1400, 1400, null),
        ledgerRow("MATCH", first, "LOSS", -8, 1392, 1400),
        ledgerRow("MATCH", second, "WIN", 10, 1402, 1408)));

    assertEquals(1402, service.rebuildRating(opponent));
  }

  @Test
  void duplicateMatchLedgerRowsDoNotAffectReplayResult() {
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select l.id,l.source_type"), eq(user))).thenReturn(List.of(
        ledgerRow("INITIAL", null, null, 1400, 1400, null),
        ledgerRow("MATCH", first, "WIN", 8, 1408, 1400),
        ledgerRow("MATCH", first, "WIN", 8, 1408, 1400),
        ledgerRow("MATCH", second, "LOSS", -10, 1398, 1392)));

    assertEquals(1398, service.rebuildRating(user));
  }

  @Test
  void userWithoutLedgerReplaysToDefaultInitialRating() {
    when(jdbc.queryForList(startsWith("select l.id,l.source_type"), eq(user))).thenReturn(List.of());

    assertEquals(1400, service.rebuildRating(user));
  }

  private static Map<String, Object> ledgerRow(String type, UUID matchId, String result, Integer delta, Integer after, Integer opponentBefore) {
    Map<String, Object> row = new HashMap<>();
    row.put("source_type", type);
    row.put("match_id", matchId);
    row.put("result", result);
    row.put("rating_delta", delta);
    row.put("rating_after", after);
    row.put("opponent_before", opponentBefore);
    return row;
  }
}
