package org.opensourcepingpong.rating;

import java.util.*;
import org.opensourcepingpong.rating.domain.RatingCalculator;
import org.opensourcepingpong.rating.domain.Winner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rebuilds a user's rating by replaying the rating ledger from the INITIAL entry. */
@Service
public class RatingLedgerReplayService {
  private static final String REPLAY_QUERY =
      "select l.id,l.source_type,l.match_id,l.rating_delta,l.rating_after,l.result,o.rating_before opponent_before"
      + " from rating_ledger l left join rating_ledger o on o.match_id=l.match_id and o.user_id=l.opponent_user_id"
      + " where l.user_id=? order by l.created_at asc,l.id asc";

  private final JdbcTemplate jdbc;
  public RatingLedgerReplayService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional(readOnly = true)
  public int rebuildRating(UUID userId) {
    var rows = jdbc.queryForList(REPLAY_QUERY, userId);
    int running = 1400;
    boolean initialSeen = false;
    Set<UUID> replayedMatches = new HashSet<>();
    for (var row : rows) {
      String type = (String) row.get("source_type");
      if ("INITIAL".equals(type)) {
        if (!initialSeen) { running = ((Number) row.get("rating_after")).intValue(); initialSeen = true; }
      } else if ("MATCH".equals(type)) {
        UUID matchId = (UUID) row.get("match_id");
        if (matchId == null || !replayedMatches.add(matchId)) continue; // (match_id,user_id) 唯一约束兜底，服务层去重
        running = replayMatch(running, row);
      }
    }
    return running;
  }

  private int replayMatch(int running, Map<String, Object> row) {
    int delta = ((Number) row.get("rating_delta")).intValue();
    Object opponentBefore = row.get("opponent_before");
    if (opponentBefore == null) return running + delta;
    boolean won = "WIN".equals(row.get("result"));
    return RatingCalculator.calculate(running, ((Number) opponentBefore).intValue(), won ? Winner.A : Winner.B).afterA();
  }
}
