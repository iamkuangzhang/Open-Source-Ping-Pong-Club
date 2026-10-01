package org.opensourcepingpong.rating;

import java.util.UUID;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class RatingController {
 private final JdbcTemplate jdbc; public RatingController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping({"/ratings/ranking","/ratings/leaderboard"}) ApiResponse<?> ranking(){return ApiResponse.ok(jdbc.queryForList("select r.user_id,r.current_rating,r.highest_rating,r.rated_matches,r.wins,r.losses,p.nickname,p.avatar_url from rating_accounts r join user_profiles p on p.user_id=r.user_id order by r.current_rating desc,r.rated_matches desc,r.user_id"));}
 @GetMapping("/ratings/me") ApiResponse<?> me(@AuthenticationPrincipal AuthPrincipal p){return ApiResponse.ok(jdbc.queryForList("select user_id,current_rating,highest_rating,rated_matches,wins,losses from rating_accounts where user_id=?",p.userId()).stream().findFirst().orElse(null));}
 @GetMapping("/ratings/me/history") ApiResponse<?> history(@AuthenticationPrincipal AuthPrincipal p){return ApiResponse.ok(jdbc.queryForList("select id,source_type,competition_id,match_id,opponent_user_id,rating_before,rating_delta,rating_after,result,created_at from rating_ledger where user_id=? order by created_at desc",p.userId()));}
 @GetMapping({"/ratings/{userId}/history","/ratings/users/{userId}/history"}) ApiResponse<?> publicHistory(@PathVariable UUID userId){return ApiResponse.ok(jdbc.queryForList("select source_type,competition_id,match_id,rating_before,rating_delta,rating_after,result,created_at from rating_ledger where user_id=? order by created_at desc",userId));}
 @GetMapping("/ratings/users/{userId}") ApiResponse<?> userRating(@PathVariable UUID userId){return ApiResponse.ok(jdbc.queryForList("select user_id,current_rating,highest_rating,rated_matches,wins,losses from rating_accounts where user_id=?",userId).stream().findFirst().orElse(null));}
}
