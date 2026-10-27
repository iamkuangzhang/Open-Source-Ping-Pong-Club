package org.opensourcepingpong.rating;

import java.util.*;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.competition.CompetitionStateMachine;
import org.opensourcepingpong.competition.CompetitionStatus;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.opensourcepingpong.rating.domain.RatingCalculator;
import org.opensourcepingpong.rating.domain.RatingChange;
import org.opensourcepingpong.rating.domain.Winner;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Settles a rated competition once, in completed-match order. */
@Service
public class RatingSettlementService {
  private final JdbcTemplate jdbc;
  private final AuthorizationService authorization;
  public RatingSettlementService(JdbcTemplate jdbc, AuthorizationService authorization) { this.jdbc = jdbc; this.authorization = authorization; }

  @Transactional
  public void finalizeCompetition(UUID operator, UUID competitionId) {
    authorization.require(operator, Permission.COMPETITION_MANAGE);
    var competition = one("select competition_type,status,s.code sport_code from competitions c join sports s on s.id=c.sport_id where c.id=? for update", competitionId, "COMPETITION_NOT_FOUND");
    var prior = jdbc.queryForList("select status from rating_settlements where competition_id=?", competitionId);
    if (!prior.isEmpty() && "COMPLETED".equals(prior.getFirst().get("status")) && "FINISHED".equals(competition.get("status"))) return;
    if (!"RESULT_CONFIRMING".equals(competition.get("status"))) {
      throw new BusinessException(HttpStatus.CONFLICT, "COMPETITION_INVALID_STATE", "competition must be in result confirmation");
    }
    if ("RATED".equals(competition.get("competition_type")) && !"PING_PONG".equals(competition.get("sport_code"))) {
      throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SPORT_RATING_UNSUPPORTED", "其他运动类型暂不支持积分赛事");
    }
    if (!prior.isEmpty() && "COMPLETED".equals(prior.getFirst().get("status"))) return; // idempotent
    UUID settlementId;
    if (prior.isEmpty()) {
      settlementId = UUID.randomUUID();
      jdbc.update("insert into rating_settlements(id,competition_id,rule_version,status) values(?,?,?,?)", settlementId, competitionId, "V1", "PROCESSING");
    } else {
      settlementId = jdbc.queryForObject("select id from rating_settlements where competition_id=?", UUID.class, competitionId);
      jdbc.update("update rating_settlements set status='PROCESSING' where id=?", settlementId);
    }
    if ("RATED".equals(competition.get("competition_type"))) {
      var matches = jdbc.queryForList("select id,player_a_user_id,player_b_user_id,winner_user_id,loser_user_id from matches where competition_id=? and status='COMPLETED' and result_type='NORMAL' order by completed_at,id", competitionId);
      for (var match : matches) settleMatch(settlementId, competitionId, match);
    }
    jdbc.update("update rating_settlements set status='COMPLETED',completed_at=current_timestamp where id=?", settlementId);
    jdbc.update("update competitions set status=? where id=?", CompetitionStateMachine.next(CompetitionStatus.RESULT_CONFIRMING, "finalize").name(), competitionId);
  }

  private void settleMatch(UUID settlementId, UUID competitionId, Map<String,Object> match) {
    UUID matchId=(UUID)match.get("id"), a=(UUID)match.get("player_a_user_id"), b=(UUID)match.get("player_b_user_id"), winner=(UUID)match.get("winner_user_id");
    if (!active(a) || !active(b)) return;
    if (!jdbc.queryForList("select id from rating_ledger where match_id=? and source_type='MATCH'", matchId).isEmpty()) return;
    var accounts = lockedAccounts(a, b);
    int ar=((Number)accounts.get(a).get("current_rating")).intValue(), br=((Number)accounts.get(b).get("current_rating")).intValue();
    RatingChange change=RatingCalculator.calculate(ar,br,winner.equals(a)?Winner.A:Winner.B);
    updateAccount(a,change.afterA(),change.deltaA()>0); updateAccount(b,change.afterB(),change.deltaB()>0);
    ledger(settlementId,competitionId,matchId,a,b,ar,change.deltaA(),change.afterA(),change.deltaA()>0?"WIN":"LOSS");
    ledger(settlementId,competitionId,matchId,b,a,br,change.deltaB(),change.afterB(),change.deltaB()>0?"WIN":"LOSS");
    jdbc.update("update matches set rating_eligible=true where id=?",matchId);
  }
  private boolean active(UUID user) { return jdbc.queryForList("select id from memberships where user_id=? and status='ACTIVE'",user).size()==1; }
  private Map<UUID,Map<String,Object>> lockedAccounts(UUID a, UUID b) {
    var ids = new ArrayList<>(List.of(a, b));
    ids.sort(Comparator.comparing(UUID::toString));
    var rows = jdbc.queryForList("select user_id,current_rating from rating_accounts where user_id in (?,?) order by user_id for update", ids.get(0), ids.get(1));
    if (rows.size() != 2) throw new BusinessException(HttpStatus.NOT_FOUND, "RATING_ACCOUNT_NOT_FOUND", "rating account not found");
    Map<UUID,Map<String,Object>> byUser = new HashMap<>();
    for (var row : rows) byUser.put((UUID) row.get("user_id"), row);
    return byUser;
  }
  private void updateAccount(UUID user,int rating,boolean won) { jdbc.update("update rating_accounts set current_rating=?,highest_rating=greatest(highest_rating,?),rated_matches=rated_matches+1,wins=wins+?,losses=losses+?,version=version+1,updated_at=current_timestamp where user_id=?",rating,rating,won?1:0,won?0:1,user); }
  private void ledger(UUID settlement,UUID competition,UUID match,UUID user,UUID opponent,int before,int delta,int after,String result) { jdbc.update("insert into rating_ledger(id,user_id,source_type,source_id,competition_id,match_id,opponent_user_id,rating_before,rating_delta,rating_after,result,rule_version,settlement_id) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),user,"MATCH",match,competition,match,opponent,before,delta,after,result,"V1",settlement); }
  private Map<String,Object> one(String sql,Object id,String code) { var rows=jdbc.queryForList(sql,id); if(rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND,code,"resource not found"); return rows.getFirst(); }
}
