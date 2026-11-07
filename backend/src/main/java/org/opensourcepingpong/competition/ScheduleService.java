package org.opensourcepingpong.competition;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.opensourcepingpong.competition.algorithm.*;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {
 private final JdbcTemplate jdbc;private final AuthorizationService auth;
 public ScheduleService(JdbcTemplate jdbc,AuthorizationService auth){this.jdbc=jdbc;this.auth=auth;}
 public Map<String,Object> preview(UUID admin,UUID competition,long seed){auth.require(admin,Permission.COMPETITION_MANAGE);var c=one("select format,status from competitions where id=?",competition,"COMPETITION_NOT_FOUND");if(!"ARRANGING".equals(c.get("status")))throw state();List<UUID> users=approved(competition);String format=(String)c.get("format");List<Map<String,Object>> matches=new ArrayList<>();if("SINGLE_ELIMINATION".equals(format)){List<UUID> slots=SingleEliminationBracketGenerator.arrange(users,seed);int rounds=Integer.numberOfTrailingZeros(slots.size());for(int round=1;round<=rounds;round++){int count=slots.size()>>round;for(int match=1;match<=count;match++){UUID a=round==1?slots.get((match-1)*2):null,b=round==1?slots.get((match-1)*2+1):null;Map<String,Object> row=new LinkedHashMap<>();row.put("round",round);row.put("match",match);row.put("playerA",a);row.put("playerB",b);row.put("bye",round==1&&(a==null||b==null));row.put("nextMatch",round<rounds?(match+1)/2:null);matches.add(row);}}}else{var rounds=RoundRobinScheduleGenerator.generate(users);for(int round=0;round<rounds.size();round++)for(var pair:rounds.get(round)){Map<String,Object> row=new LinkedHashMap<>();row.put("round",round+1);row.put("playerA",pair.a());row.put("playerB",pair.b());matches.add(row);}}return Map.of("format",format,"seed",seed,"participantCount",users.size(),"matches",matches);}
 @Transactional public void arrange(UUID admin,UUID competition,long seed){auth.require(admin,Permission.COMPETITION_MANAGE);var c=one("select format,best_of,status from competitions where id=? for update",competition,"COMPETITION_NOT_FOUND");if(!"ARRANGING".equals(c.get("status")))throw state();List<UUID> users=approved(competition);UUID stage=UUID.randomUUID();String format=(String)c.get("format");jdbc.update("insert into competition_stages(id,competition_id,name,stage_type,stage_order)values(?,?,?, ?,1)",stage,competition,"主赛程","SINGLE_ELIMINATION".equals(format)?"KNOCKOUT":"GROUP");if("SINGLE_ELIMINATION".equals(format)) knockout(competition,stage,users,seed,((Number)c.get("best_of")).intValue());else robin(competition,stage,users,((Number)c.get("best_of")).intValue());jdbc.update("update competitions set status='READY',arrangement_seed=?,updated_at=? where id=?",seed,Timestamp.from(Instant.now()),competition);}
 private List<UUID> approved(UUID competition){List<UUID> users=jdbc.queryForList("select user_id from competition_entries where competition_id=? and status='APPROVED' order by user_id",UUID.class,competition);if(users.size()<2)throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"COMPETITION_INSUFFICIENT_PARTICIPANTS","参赛人数不足");return users;}
 private void knockout(UUID c,UUID stage,List<UUID> users,long seed,int best){List<UUID> slots=SingleEliminationBracketGenerator.arrange(users,seed);int size=slots.size(),rounds=Integer.numberOfTrailingZeros(size);Map<Integer,List<UUID>> matches=new HashMap<>();for(int round=1;round<=rounds;round++){int count=size>>(round);List<UUID> ids=new ArrayList<>();for(int n=1;n<=count;n++){UUID id=UUID.randomUUID();ids.add(id);jdbc.update("insert into matches(id,competition_id,stage_id,round_number,match_number,status,best_of)values(?,?,?,?,?,'READY',?)",id,c,stage,round,n,best);}matches.put(round,ids);}for(int r=1;r<rounds;r++)for(int i=0;i<matches.get(r).size();i++){UUID next=matches.get(r+1).get(i/2);jdbc.update("update matches set next_match_id=?,winner_to_slot=? where id=?",next,i%2==0?"A":"B",matches.get(r).get(i));}for(int i=0;i<size/2;i++){UUID id=matches.get(1).get(i),a=slots.get(i*2),b=slots.get(i*2+1);jdbc.update("update matches set player_a_user_id=?,player_b_user_id=? where id=?",a,b,id);if(a==null||b==null){UUID winner=a!=null?a:b;jdbc.update("update matches set status='COMPLETED',result_type='BYE',winner_user_id=?,completed_at=? where id=?",winner,Timestamp.from(Instant.now()),id);if(winner!=null)advance(id,winner);}}}
 private void robin(UUID c,UUID stage,List<UUID> users,int best){UUID group=UUID.randomUUID();jdbc.update("insert into competition_groups(id,stage_id,name,group_order)values(?,?,?,1)",group,stage,"A组");for(int i=0;i<users.size();i++)jdbc.update("insert into competition_group_members(id,group_id,user_id,position)values(?,?,?,?)",UUID.randomUUID(),group,users.get(i),i+1);int num=1;var schedule=RoundRobinScheduleGenerator.generate(users);for(int i=0;i<schedule.size();i++)for(var pair:schedule.get(i))jdbc.update("insert into matches(id,competition_id,stage_id,group_id,round_number,match_number,status,best_of,player_a_user_id,player_b_user_id)values(?,?,?,?,?,?,'READY',?,?,?)",UUID.randomUUID(),c,stage,group,i+1,num++,best,pair.a(),pair.b());}
 public void start(UUID admin,UUID c){transition(admin,c,"start");} public void confirming(UUID admin,UUID c){auth.require(admin,Permission.COMPETITION_MANAGE);Integer pending=jdbc.queryForObject("select count(*) from matches where competition_id=? and status<>'COMPLETED'",Integer.class,c);if(pending!=null&&pending>0)throw new BusinessException(HttpStatus.CONFLICT,"COMPETITION_MATCHES_INCOMPLETE","all matches must be completed");transition(admin,c,"confirm");}private void transition(UUID admin,UUID id,String a){auth.require(admin,Permission.COMPETITION_MANAGE);var r=one("select status from competitions where id=?",id,"COMPETITION_NOT_FOUND");jdbc.update("update competitions set status=? where id=?",CompetitionStateMachine.next(CompetitionStatus.valueOf((String)r.get("status")),a).name(),id);}
 @Transactional public void advance(UUID match,UUID winner){
  var source=one("select competition_id,status,winner_user_id,next_match_id,winner_to_slot from matches where id=?",match,"MATCH_NOT_FOUND");
  if(!"COMPLETED".equals(source.get("status"))||!winner.equals(source.get("winner_user_id")))throw advancementConflict();
  if(source.get("next_match_id")==null)return;
  UUID next=(UUID)source.get("next_match_id");
  String slot=(String)source.get("winner_to_slot");
  if(!"A".equals(slot)&&!"B".equals(slot))throw advancementConflict();
  String column="A".equals(slot)?"player_a_user_id":"player_b_user_id";
  int changed=jdbc.update("update matches set "+column+"=?,version=version+1 where id=? and competition_id=? and status='READY' and "+column+" is null",winner,next,source.get("competition_id"));
  if(changed==1)return;
  var target=one("select competition_id,"+column+" from matches where id=?",next,"MATCH_NOT_FOUND");
  if(!source.get("competition_id").equals(target.get("competition_id"))||!winner.equals(target.get(column)))throw advancementConflict();
 }
 private static BusinessException advancementConflict(){return new BusinessException(HttpStatus.CONFLICT,"MATCH_ADVANCEMENT_CONFLICT","下一场参赛席位已被占用或晋级关系无效");}
 private Map<String,Object> one(String sql,Object id,String code){var r=jdbc.queryForList(sql,id);if(r.isEmpty())throw new BusinessException(HttpStatus.NOT_FOUND,code,"资源不存在");return r.getFirst();}private static BusinessException state(){return new BusinessException(HttpStatus.CONFLICT,"COMPETITION_INVALID_STATE","比赛状态错误");}
}
