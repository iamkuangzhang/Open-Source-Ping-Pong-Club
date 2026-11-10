package org.opensourcepingpong.competition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.competition.scoring.ScoreTemplate;
import org.opensourcepingpong.competition.scoring.Sport;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 3 / E6-D: score submission, advancement, group standings and the
 * 30-minute correction window. Matches that are edited after the window create
 * an approval request instead of mutating the result directly.
 */
@Service
public class ScoreSubmissionService {
    static final long EDIT_WINDOW_SECONDS = 30 * 60;
    private final JdbcTemplate jdbc;
    private final AuthorizationService authorization;
    private final ScheduleService schedules;
    private final ObjectMapper mapper;

    public ScoreSubmissionService(
        JdbcTemplate jdbc,
        AuthorizationService authorization,
        ScheduleService schedules,
        ObjectMapper mapper
    ) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.schedules = schedules;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> submit(UUID operator, UUID matchId, ScoreRequest request, String idempotencyKey) {
        authorization.require(operator, Permission.MATCH_SCORE_EDIT);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = jdbc.queryForList(
                "select id from match_score_logs where match_id=? and idempotency_key=?",
                matchId, idempotencyKey);
            if (!existing.isEmpty()) {
                Map<String, Object> replay = new LinkedHashMap<>();
                replay.put("matchId", matchId);
                replay.put("idempotent", true);
                replay.put("status", "COMPLETED");
                return replay;
            }
        }
        var match = load(matchId);
        if ("COMPLETED".equals(match.get("status"))) throw settled();
        if (!"PLAYING".equals(match.get("status")) || match.get("player_a_user_id") == null
            || match.get("player_b_user_id") == null) throw state();

        List<ScoreTemplate.Game> games = games(request.games());
        int winnerSlot = validate(match, games);
        UUID winner = (UUID) match.get(winnerSlot == 0 ? "player_a_user_id" : "player_b_user_id");
        UUID loser = (UUID) match.get(winnerSlot == 0 ? "player_b_user_id" : "player_a_user_id");
        int aWins = ScoreTemplate.gameWins(games, 0);
        int bWins = ScoreTemplate.gameWins(games, 1);
        Instant now = Instant.now();
        int changed = jdbc.update("""
            update matches set status='COMPLETED',result_type='NORMAL',winner_user_id=?,loser_user_id=?,
                player_a_games=?,player_b_games=?,completed_at=?,score_submitted_at=?,score_edit_deadline=?,
                version=version+1
            where id=? and version=? and status='PLAYING'
            """, winner, loser, aWins, bWins, Timestamp.from(now), Timestamp.from(now),
            Timestamp.from(now.plusSeconds(EDIT_WINDOW_SECONDS)), matchId, request.version());
        if (changed != 1) throw conflict();

        replaceGames(matchId, games, (UUID) match.get("player_a_user_id"), (UUID) match.get("player_b_user_id"));
        log(matchId, "SUBMIT", null, json(games), operator, request.reason(), idempotencyKey);
        schedules.advance(matchId, winner);
        audit(operator, "MATCH_RESULT_CONFIRMED", matchId, null, json(games), request.reason());
        return result(matchId, "COMPLETED", winner, request.version() + 1);
    }

    @Transactional
    public Map<String, Object> edit(UUID operator, UUID matchId, ScoreRequest request) {
        authorization.require(operator, Permission.MATCH_SCORE_EDIT);
        var match = load(matchId);
        if (!"COMPLETED".equals(match.get("status")) || !"NORMAL".equals(match.get("result_type"))) {
            throw state();
        }
        if (((Number) match.get("version")).longValue() != request.version()) throw conflict();
        List<ScoreTemplate.Game> games = games(request.games());
        validate(match, games);

        Instant deadline = instant(match.get("score_edit_deadline"));
        if (deadline != null && Instant.now().isAfter(deadline)) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "MATCH_SCORE_REQUIRES_REASON", "超过 30 分钟修改窗口，需填写修改原因并等待管理员审批");
            }
            UUID requestId = UUID.randomUUID();
            try {
                jdbc.update("""
                    insert into match_score_change_requests(id,match_id,requested_by,match_version,reason,
                        before_json,after_json,status)
                    values(?,?,?,?,?,?::jsonb,?::jsonb,'PENDING')
                    """, requestId, matchId, operator, request.version(), request.reason(),
                    json(currentGames(matchId)), json(games));
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(HttpStatus.CONFLICT,
                    "MATCH_SCORE_CHANGE_PENDING", "该场次已有待审批的比分修改");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("matchId", matchId);
            result.put("status", "PENDING_APPROVAL");
            result.put("requestId", requestId);
            return result;
        }

        applyEdit(match, games, operator, request.reason(), "UPDATE");
        var updated = load(matchId);
        return result(matchId, "COMPLETED", (UUID) updated.get("winner_user_id"),
            ((Number) updated.get("version")).longValue());
    }

    @Transactional
    public Map<String, Object> approveChange(UUID operator, UUID matchId, UUID changeId, String note) {
        authorization.require(operator, Permission.COMPETITION_MANAGE);
        var requests = jdbc.queryForList("""
            select id,status,match_version,after_json,reason from match_score_change_requests
            where id=? and match_id=? for update
            """, changeId, matchId);
        if (requests.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "MATCH_SCORE_CHANGE_NOT_FOUND", "比分修改申请不存在");
        }
        var change = requests.getFirst();
        if (!"PENDING".equals(change.get("status"))) {
            throw new BusinessException(HttpStatus.CONFLICT, "MATCH_SCORE_CHANGE_ALREADY_REVIEWED", "修改申请已处理");
        }
        var match = load(matchId);
        if (((Number) match.get("version")).longValue() != ((Number) change.get("match_version")).longValue()) {
            throw conflict();
        }
        List<ScoreTemplate.Game> games = parseGames(String.valueOf(change.get("after_json")));
        validate(match, games);
        applyEdit(match, games, operator, String.valueOf(change.get("reason")), "APPROVED_CHANGE");
        jdbc.update("""
            update match_score_change_requests set status='APPROVED',reviewed_by=?,reviewed_at=?,review_note=?
            where id=? and status='PENDING'
            """, operator, Timestamp.from(Instant.now()), note, changeId);
        var updated = load(matchId);
        return result(matchId, "COMPLETED", (UUID) updated.get("winner_user_id"),
            ((Number) updated.get("version")).longValue());
    }

    @Transactional
    public Map<String, Object> rejectChange(UUID operator, UUID matchId, UUID changeId, String note) {
        authorization.require(operator, Permission.COMPETITION_MANAGE);
        int changed = jdbc.update("""
            update match_score_change_requests set status='REJECTED',reviewed_by=?,reviewed_at=?,review_note=?
            where id=? and match_id=? and status='PENDING'
            """, operator, Timestamp.from(Instant.now()), note, changeId, matchId);
        if (changed != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "MATCH_SCORE_CHANGE_ALREADY_REVIEWED", "修改申请已处理");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", matchId);
        result.put("requestId", changeId);
        result.put("status", "REJECTED");
        return result;
    }

    @Transactional
    public Map<String, Object> walkover(UUID operator, UUID matchId, ResultRequest request) {
        authorization.require(operator, Permission.MATCH_SCORE_EDIT);
        var match = load(matchId);
        if ("COMPLETED".equals(match.get("status"))) throw settled();
        UUID a = (UUID) match.get("player_a_user_id");
        UUID b = (UUID) match.get("player_b_user_id");
        if (a == null || b == null) throw state();
        UUID winner = request.winnerId();
        if (winner == null || (!winner.equals(a) && !winner.equals(b))) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "弃权胜者必须是参赛者之一");
        }
        UUID loser = winner.equals(a) ? b : a;
        String reason = request.reason() == null ? "" : request.reason();
        int changed = jdbc.update("""
            update matches set status='COMPLETED',result_type='WALKOVER',winner_user_id=?,loser_user_id=?,
                rating_eligible=false,result_reason=?,completed_at=?,version=version+1
            where id=? and version=? and status<>'COMPLETED'
            """, winner, loser, reason, Timestamp.from(Instant.now()), matchId, request.version());
        if (changed != 1) throw conflict();
        jdbc.update("delete from match_games where match_id=?", matchId);
        log(matchId, "WALKOVER", null, "{}", operator, request.reason(), null);
        schedules.advance(matchId, winner);
        audit(operator, "MATCH_WALKOVER", matchId, null, "{}", request.reason());
        return result(matchId, "COMPLETED", winner, request.version() + 1);
    }

    @Transactional
    public Map<String, Object> retirement(UUID operator, UUID matchId, RetirementRequest request) {
        authorization.require(operator, Permission.MATCH_SCORE_EDIT);
        var match = load(matchId);
        if (!"PLAYING".equals(match.get("status"))) throw state();
        UUID a = (UUID) match.get("player_a_user_id");
        UUID b = (UUID) match.get("player_b_user_id");
        UUID retired = request.retiringUserId();
        if (retired == null || (!retired.equals(a) && !retired.equals(b))) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "退赛者必须是参赛者之一");
        }
        UUID winner = retired.equals(a) ? b : a;
        String reason = request.reason() == null ? "" : request.reason();
        int changed = jdbc.update("""
            update matches set status='COMPLETED',result_type='RETIREMENT',winner_user_id=?,loser_user_id=?,
                rating_eligible=true,result_reason=?,completed_at=?,version=version+1
            where id=? and version=? and status='PLAYING'
            """, winner, retired, reason, Timestamp.from(Instant.now()), matchId, request.version());
        if (changed != 1) throw conflict();
        log(matchId, "RETIREMENT", null, "{}", operator, request.reason(), null);
        schedules.advance(matchId, winner);
        audit(operator, "MATCH_RETIREMENT", matchId, null, "{}", request.reason());
        return result(matchId, "COMPLETED", winner, request.version() + 1);
    }

    public Map<String, Object> bracket(UUID competitionId) {
        var competitionRows = jdbc.queryForList("""
            select id,title,sport,format,status from competitions where id=?
            """, competitionId);
        if (competitionRows.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COMPETITION_NOT_FOUND", "赛事不存在");
        }
        var matches = jdbc.queryForList("""
            select id,round_number,match_number,group_id,status,result_type,player_a_user_id,
                   player_b_user_id,winner_user_id,next_match_id,winner_to_slot,best_of,
                   player_a_games,player_b_games,version
            from matches where competition_id=?
            order by round_number nulls last,match_number,id
            """, competitionId);
        var gameRows = jdbc.queryForList("""
            select g.match_id,g.game_number,g.player_a_score,g.player_b_score,g.winner_user_id
            from match_games g join matches m on m.id=g.match_id
            where m.competition_id=? order by g.match_id,g.game_number
            """, competitionId);
        Map<UUID, List<Map<String, Object>>> gamesByMatch = new LinkedHashMap<>();
        for (var game : gameRows) {
            gamesByMatch.computeIfAbsent((UUID) game.get("match_id"), key -> new ArrayList<>()).add(game);
        }
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (var match : matches) {
            Map<String, Object> row = new LinkedHashMap<>(match);
            row.put("games", gamesByMatch.getOrDefault((UUID) match.get("id"), List.of()));
            enriched.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("competition", competitionRows.getFirst());
        result.put("matches", enriched);
        result.put("standings", standings(matches));
        return result;
    }

    private void applyEdit(
        Map<String, Object> match,
        List<ScoreTemplate.Game> games,
        UUID operator,
        String reason,
        String changeType
    ) {
        UUID matchId = (UUID) match.get("id");
        UUID oldWinner = (UUID) match.get("winner_user_id");
        UUID playerA = (UUID) match.get("player_a_user_id");
        UUID playerB = (UUID) match.get("player_b_user_id");
        String before = json(currentGamesFrom(match));
        int winnerSlot = validate(match, games);
        UUID newWinner = winnerSlot == 0 ? playerA : playerB;
        UUID newLoser = winnerSlot == 0 ? playerB : playerA;
        if (oldWinner != null && !oldWinner.equals(newWinner)) {
            reviseAdvancement(match, oldWinner, newWinner);
        }
        int aWins = ScoreTemplate.gameWins(games, 0);
        int bWins = ScoreTemplate.gameWins(games, 1);
        int changed = jdbc.update("""
            update matches set winner_user_id=?,loser_user_id=?,player_a_games=?,player_b_games=?,
                version=version+1
            where id=? and version=? and status='COMPLETED'
            """, newWinner, newLoser, aWins, bWins, matchId,
            ((Number) match.get("version")).longValue());
        if (changed != 1) throw conflict();
        replaceGames(matchId, games, playerA, playerB);
        log(matchId, changeType, before, json(games), operator, reason, null);
        audit(operator, "MATCH_SCORE_EDITED", matchId, before, json(games), reason);
        if (oldWinner != null && !oldWinner.equals(newWinner)) schedules.advance(matchId, newWinner);
    }

    private void reviseAdvancement(Map<String, Object> match, UUID oldWinner, UUID newWinner) {
        UUID nextMatchId = (UUID) match.get("next_match_id");
        if (nextMatchId == null) return;
        String slot = (String) match.get("winner_to_slot");
        if (!"A".equals(slot) && !"B".equals(slot)) {
            throw new BusinessException(HttpStatus.CONFLICT, "MATCH_ADVANCEMENT_LOCKED", "晋级关系无效");
        }
        var nextRows = jdbc.queryForList(
            "select status,player_a_user_id,player_b_user_id from matches where id=? for update", nextMatchId);
        if (nextRows.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "MATCH_ADVANCEMENT_LOCKED", "下一场不存在");
        }
        var next = nextRows.getFirst();
        if (!"READY".equals(next.get("status"))) {
            throw new BusinessException(HttpStatus.CONFLICT, "MATCH_ADVANCEMENT_LOCKED", "下一场已开始，无法修改胜负");
        }
        String column = "A".equals(slot) ? "player_a_user_id" : "player_b_user_id";
        if (!oldWinner.equals(next.get(column))) {
            throw new BusinessException(HttpStatus.CONFLICT, "MATCH_ADVANCEMENT_LOCKED", "下一场参赛者已被修改");
        }
        int changed = jdbc.update("""
            update matches set %s=?,version=version+1 where id=? and %s=? and status='READY'
            """.formatted(column, column), newWinner, nextMatchId, oldWinner);
        if (changed != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "MATCH_ADVANCEMENT_LOCKED", "下一场参赛者已被修改");
        }
    }

    private List<Map<String, Object>> standings(List<Map<String, Object>> matches) {
        Map<UUID, int[]> table = new LinkedHashMap<>();
        for (var match : matches) {
            if (match.get("group_id") == null || !"COMPLETED".equals(match.get("status"))) continue;
            UUID a = (UUID) match.get("player_a_user_id");
            UUID b = (UUID) match.get("player_b_user_id");
            UUID winner = (UUID) match.get("winner_user_id");
            if (a == null || b == null || winner == null) continue;
            int[] aRow = table.computeIfAbsent(a, key -> new int[4]);
            int[] bRow = table.computeIfAbsent(b, key -> new int[4]);
            boolean aWon = winner.equals(a);
            (aWon ? aRow : bRow)[0]++;
            (aWon ? bRow : aRow)[1]++;
            int aGames = intValue(match.get("player_a_games"));
            int bGames = intValue(match.get("player_b_games"));
            aRow[2] += aGames;
            aRow[3] += bGames;
            bRow[2] += bGames;
            bRow[3] += aGames;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var entry : table.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", entry.getKey());
            row.put("wins", entry.getValue()[0]);
            row.put("losses", entry.getValue()[1]);
            row.put("gamesWon", entry.getValue()[2]);
            row.put("gamesLost", entry.getValue()[3]);
            row.put("gameDifference", entry.getValue()[2] - entry.getValue()[3]);
            rows.add(row);
        }
        rows.sort(Comparator
            .comparingInt((Map<String, Object> row) -> (Integer) row.get("wins")).reversed()
            .thenComparing(Comparator.comparingInt(
                (Map<String, Object> row) -> (Integer) row.get("gameDifference")).reversed())
            .thenComparing(row -> row.get("userId").toString()));
        return rows;
    }

    private Map<String, Object> load(UUID matchId) {
        var rows = jdbc.queryForList("""
            select m.*,c.sport from matches m join competitions c on c.id=m.competition_id
            where m.id=?
            """, matchId);
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", "场次不存在");
        }
        return rows.getFirst();
    }

    private int validate(Map<String, Object> match, List<ScoreTemplate.Game> games) {
        Sport sport = Sport.parse((String) match.get("sport"));
        try {
            return ScoreTemplate.matchWinner(sport, ((Number) match.get("best_of")).intValue(), games);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "MATCH_INVALID_SCORE",
                exception.getMessage());
        }
    }

    private void replaceGames(UUID matchId, List<ScoreTemplate.Game> games, UUID playerA, UUID playerB) {
        jdbc.update("delete from match_games where match_id=?", matchId);
        for (int index = 0; index < games.size(); index++) {
            var game = games.get(index);
            jdbc.update("""
                insert into match_games(id,match_id,game_number,player_a_score,player_b_score,winner_user_id)
                values(?,?,?,?,?,?)
                """, UUID.randomUUID(), matchId, index + 1, game.a(), game.b(),
                game.a() > game.b() ? playerA : playerB);
        }
    }

    private List<Map<String, Object>> currentGames(UUID matchId) {
        return jdbc.queryForList("""
            select game_number as "gameNumber",player_a_score as a,player_b_score as b
            from match_games where match_id=? order by game_number
            """, matchId);
    }

    private List<Map<String, Object>> currentGamesFrom(Map<String, Object> match) {
        return currentGames((UUID) match.get("id"));
    }

    private void log(
        UUID matchId,
        String changeType,
        String beforeJson,
        String afterJson,
        UUID operator,
        String reason,
        String idempotencyKey
    ) {
        jdbc.update("""
            insert into match_score_logs(id,match_id,change_type,before_json,after_json,operator_user_id,
                reason,idempotency_key)
            values(?,?,?,?::jsonb,?::jsonb,?,?,?)
            """, statement -> {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, matchId);
                statement.setString(3, changeType);
                statement.setString(4, beforeJson == null ? "null" : beforeJson);
                statement.setString(5, afterJson);
                statement.setObject(6, operator);
                statement.setString(7, reason == null ? "" : reason);
                if (idempotencyKey == null) statement.setNull(8, java.sql.Types.VARCHAR);
                else statement.setString(8, idempotencyKey);
            });
    }

    private void audit(
        UUID operator,
        String action,
        UUID targetId,
        String beforeJson,
        String afterJson,
        String reason
    ) {
        jdbc.update("""
            insert into audit_logs(id,operator_user_id,action,target_type,target_id,before_json,after_json,reason)
            values(?,?,?,'MATCH',?,?::jsonb,?::jsonb,?)
            """, UUID.randomUUID(), operator, action, targetId,
            beforeJson == null ? "null" : beforeJson, afterJson == null ? "null" : afterJson,
            reason == null ? "" : reason);
    }

    private String json(Object value) {
        if (value == null) return "null";
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize score snapshot", exception);
        }
    }

    private List<ScoreTemplate.Game> parseGames(String json) {
        try {
            var nodes = mapper.readTree(json);
            List<ScoreTemplate.Game> games = new ArrayList<>();
            for (var node : nodes) {
                games.add(new ScoreTemplate.Game(node.get("a").asInt(), node.get("b").asInt()));
            }
            return games;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot read score snapshot", exception);
        }
    }

    private static List<ScoreTemplate.Game> games(List<Game> games) {
        if (games == null) return List.of();
        return games.stream().map(game -> new ScoreTemplate.Game(game.a(), game.b())).toList();
    }

    private static int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offset) return offset.toInstant();
        throw new IllegalArgumentException("unsupported time value");
    }

    private static Map<String, Object> result(UUID matchId, String status, UUID winner, long version) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", matchId);
        result.put("status", status);
        result.put("winnerUserId", winner);
        result.put("version", version);
        return result;
    }

    private static BusinessException state() {
        return new BusinessException(HttpStatus.CONFLICT, "MATCH_INVALID_STATE", "场次状态不允许该操作");
    }

    private static BusinessException conflict() {
        return new BusinessException(HttpStatus.CONFLICT, "MATCH_VERSION_CONFLICT", "比赛数据已被修改");
    }

    private static BusinessException settled() {
        return new BusinessException(HttpStatus.CONFLICT, "MATCH_ALREADY_SETTLED", "场次已结算");
    }

    public record Game(int a, int b) {}

    public record ScoreRequest(
        long version,
        @jakarta.validation.constraints.NotEmpty List<Game> games,
        String reason
    ) {}

    public record ResultRequest(UUID winnerId, long version, String reason) {}

    public record RetirementRequest(UUID retiringUserId, long version, String reason) {}
}
