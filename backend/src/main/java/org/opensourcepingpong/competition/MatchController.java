package org.opensourcepingpong.competition;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.ApiResponse;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.competition.algorithm.ScoreValidator;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MatchController {
    private final JdbcTemplate jdbc;
    private final AuthorizationService auth;
    private final ScheduleService schedules;

    public MatchController(JdbcTemplate jdbc, AuthorizationService auth, ScheduleService schedules) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.schedules = schedules;
    }

    @GetMapping("/matches/{id}")
    ApiResponse<?> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("select * from matches where id=?", id);
        if (rows.isEmpty()) throw missing();
        return ApiResponse.ok(rows.getFirst());
    }

    @PostMapping("/admin/matches/{id}/start")
    ApiResponse<Void> start(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        auth.require(principal.userId(), Permission.MATCH_SCORE_EDIT);
        int changed = jdbc.update("""
            update matches m set status='PLAYING',started_at=?,version=version+1
            where m.id=? and m.status='READY'
              and m.player_a_user_id is not null and m.player_b_user_id is not null
            """, Timestamp.from(Instant.now()), id);
        if (changed != 1) throw state();
        return ApiResponse.ok(null);
    }

    @Transactional
    @PutMapping("/admin/matches/{id}/score")
    ApiResponse<Void> score(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                            @Valid @RequestBody ScoreRequest request) {
        auth.require(principal.userId(), Permission.MATCH_SCORE_EDIT);
        var rows = jdbc.queryForList(
            "select player_a_user_id,player_b_user_id,best_of,status,version from matches where id=?", id);
        if (rows.isEmpty()) throw missing();
        var match = rows.getFirst();
        if (((Number) match.get("version")).longValue() != request.version()) throw conflict();
        if (!"PLAYING".equals(match.get("status"))
            || match.get("player_a_user_id") == null || match.get("player_b_user_id") == null) throw state();

        List<ScoreValidator.Game> games = request.games().stream()
            .map(game -> new ScoreValidator.Game(game.a(), game.b())).toList();
        int winningSlot;
        try {
            winningSlot = ScoreValidator.matchWinner(((Number) match.get("best_of")).intValue(), games);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "MATCH_INVALID_SCORE", "比分不合法");
        }
        UUID winner = (UUID) match.get(winningSlot == 0 ? "player_a_user_id" : "player_b_user_id");
        UUID loser = (UUID) match.get(winningSlot == 0 ? "player_b_user_id" : "player_a_user_id");
        int changed = jdbc.update("""
            update matches m set status='COMPLETED',result_type='NORMAL',winner_user_id=?,loser_user_id=?,
                player_a_games=?,player_b_games=?,completed_at=?,version=version+1
            where m.id=? and m.version=? and m.status='PLAYING'
            """, winner, loser,
            (int) games.stream().filter(game -> ScoreValidator.winner(game) == 0).count(),
            (int) games.stream().filter(game -> ScoreValidator.winner(game) == 1).count(),
            Timestamp.from(Instant.now()), id, request.version());
        if (changed != 1) throw conflict();

        jdbc.update("delete from match_games where match_id=?", id);
        for (int index = 0; index < games.size(); index++) {
            var game = games.get(index);
            jdbc.update("""
                insert into match_games(id,match_id,game_number,player_a_score,player_b_score,winner_user_id)
                values(?,?,?,?,?,?)
                """, UUID.randomUUID(), id, index + 1, game.a(), game.b(),
                ScoreValidator.winner(game) == 0 ? match.get("player_a_user_id") : match.get("player_b_user_id"));
        }
        schedules.advance(id, winner);
        return ApiResponse.ok(null);
    }

    private static BusinessException missing() {
        return new BusinessException(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", "场次不存在");
    }

    private static BusinessException conflict() {
        return new BusinessException(HttpStatus.CONFLICT, "MATCH_VERSION_CONFLICT", "比赛数据已被修改");
    }

    private static BusinessException state() {
        return new BusinessException(HttpStatus.CONFLICT, "MATCH_INVALID_STATE", "场次状态不允许该操作");
    }

    public record ScoreRequest(long version, @NotEmpty List<Game> games) {}
    public record Game(int a, int b) {}
}
