package org.opensourcepingpong.competition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** Runs against the configured local PostgreSQL and rolls back every fixture. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class MatchAdvancementIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ScheduleService schedules;
    @Autowired MatchController matches;
    @Autowired MockMvc mvc;
    @MockitoBean AuthorizationService authorization;

    @Test
    void eightPlayersAdvanceThroughEveryRoundToOneChampion() {
        Fixture fixture = fixture(8);
        schedules.arrange(fixture.manager(), fixture.competition(), 42L);
        var bracket = bracket(fixture.competition());
        assertEquals(7, bracket.size());
        assertEquals(4, bracket.stream().filter(row -> round(row) == 1).count());
        assertEquals(2, bracket.stream().filter(row -> round(row) == 2).count());
        assertEquals(1, bracket.stream().filter(row -> round(row) == 3).count());
        assertEquals(6, bracket.stream().filter(row -> row.get("next_match_id") != null).count());
        schedules.start(fixture.manager(), fixture.competition());

        for (int round = 1; round <= 3; round++) {
            for (var scheduled : bracket(fixture.competition())) {
                if (round(scheduled) != round) continue;
                UUID id = (UUID) scheduled.get("id");
                assertNotNull(scheduled.get("player_a_user_id"));
                assertNotNull(scheduled.get("player_b_user_id"));
                long readyVersion = version(scheduled);
                matches.start(fixture.principal(), id);
                var playing = match(id);
                assertEquals("PLAYING", playing.get("status"));
                assertEquals(readyVersion + 1, version(playing));
                var score = new MatchController.ScoreRequest(version(playing), List.of(
                    new MatchController.Game(11, 8), new MatchController.Game(11, 9)));
                matches.score(fixture.principal(), id, score);
                var completed = match(id);
                assertEquals("COMPLETED", completed.get("status"));
                assertEquals(completed.get("player_a_user_id"), completed.get("winner_user_id"));
                assertEquals(2, jdbc.queryForObject(
                    "select count(*) from match_games where match_id=?", Integer.class, id));
                assertEquals("MATCH_VERSION_CONFLICT", assertThrows(BusinessException.class,
                    () -> matches.score(fixture.principal(), id, score)).code());
                if (completed.get("next_match_id") != null) {
                    var next = match((UUID) completed.get("next_match_id"));
                    String column = "A".equals(completed.get("winner_to_slot"))
                        ? "player_a_user_id" : "player_b_user_id";
                    assertEquals(completed.get("winner_user_id"), next.get(column));
                }
            }
        }

        var finished = bracket(fixture.competition());
        assertTrue(finished.stream().allMatch(row -> "COMPLETED".equals(row.get("status"))));
        var finals = finished.stream().filter(row -> row.get("next_match_id") == null).toList();
        assertEquals(1, finals.size());
        assertNotNull(finals.getFirst().get("winner_user_id"));
        schedules.confirming(fixture.manager(), fixture.competition());
        assertEquals("RESULT_CONFIRMING", jdbc.queryForObject(
            "select status from competitions where id=?", String.class, fixture.competition()));
    }

    @Test
    void byesAdvanceOnceWithoutRatingOrOverwritingAnotherPlayer() {
        Fixture fixture = fixture(5);
        schedules.arrange(fixture.manager(), fixture.competition(), 99L);
        var bracket = bracket(fixture.competition());
        var byes = bracket.stream().filter(row -> "BYE".equals(row.get("result_type"))).toList();
        assertEquals(3, byes.size());
        assertEquals(7, bracket.size());
        assertEquals(5, bracket.stream().filter(row -> round(row) == 1)
            .flatMap(row -> java.util.stream.Stream.of(row.get("player_a_user_id"), row.get("player_b_user_id")))
            .filter(java.util.Objects::nonNull).count());
        var firstRoundPlayers = new HashSet<Object>();
        bracket.stream().filter(row -> round(row) == 1).forEach(row -> {
            if (row.get("player_a_user_id") != null) assertTrue(firstRoundPlayers.add(row.get("player_a_user_id")));
            if (row.get("player_b_user_id") != null) assertTrue(firstRoundPlayers.add(row.get("player_b_user_id")));
        });

        for (var bye : byes) {
            UUID id = (UUID) bye.get("id");
            assertEquals("COMPLETED", bye.get("status"));
            assertEquals(false, bye.get("rating_eligible"));
            assertEquals(0, ((Number) bye.get("player_a_games")).intValue());
            assertEquals(0, ((Number) bye.get("player_b_games")).intValue());
            UUID winner = (UUID) bye.get("winner_user_id");
            assertNotNull(winner);
            var next = match((UUID) bye.get("next_match_id"));
            String column = "A".equals(bye.get("winner_to_slot"))
                ? "player_a_user_id" : "player_b_user_id";
            assertEquals(winner, next.get(column));
            long version = version(next);
            schedules.advance(id, winner);
            assertEquals(version, version(match((UUID) bye.get("next_match_id"))));
        }

        var bye = byes.getFirst();
        UUID nextId = (UUID) bye.get("next_match_id");
        String column = "A".equals(bye.get("winner_to_slot")) ? "player_a_user_id" : "player_b_user_id";
        UUID other = fixture.players().stream()
            .filter(player -> !player.equals(bye.get("winner_user_id"))).findFirst().orElseThrow();
        jdbc.update("update matches set " + column + "=? where id=?", other, nextId);
        assertEquals("MATCH_ADVANCEMENT_CONFLICT", assertThrows(BusinessException.class,
            () -> schedules.advance((UUID) bye.get("id"), (UUID) bye.get("winner_user_id"))).code());
        assertEquals(other, match(nextId).get(column));
    }

    @Test
    void roundTwoCannotStartBeforeBothWinnersArrive() {
        Fixture fixture = fixture(8);
        schedules.arrange(fixture.manager(), fixture.competition(), 7L);
        schedules.start(fixture.manager(), fixture.competition());
        var semiFinal = bracket(fixture.competition()).stream().filter(row -> round(row) == 2).findFirst().orElseThrow();
        assertEquals("MATCH_INVALID_STATE", assertThrows(BusinessException.class,
            () -> matches.start(fixture.principal(), (UUID) semiFinal.get("id"))).code());
    }

    @Test
    void oldVersionReturnsHttp409WithoutChangingCompletedResult() throws Exception {
        Fixture fixture = fixture(8);
        schedules.arrange(fixture.manager(), fixture.competition(), 23L);
        schedules.start(fixture.manager(), fixture.competition());
        UUID matchId = (UUID) bracket(fixture.competition()).getFirst().get("id");
        matches.start(fixture.principal(), matchId);
        long version = version(match(matchId));
        var score = new MatchController.ScoreRequest(version, List.of(
            new MatchController.Game(11, 8), new MatchController.Game(11, 9)));
        matches.score(fixture.principal(), matchId, score);
        UUID winner = (UUID) match(matchId).get("winner_user_id");

        mvc.perform(put("/api/v1/admin/matches/{id}/score", matchId)
                .with(authentication(new UsernamePasswordAuthenticationToken(
                    fixture.principal(), null, List.of())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + ",\"games\":[{\"a\":11,\"b\":8},{\"a\":11,\"b\":9}]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MATCH_VERSION_CONFLICT"));
        assertEquals(winner, match(matchId).get("winner_user_id"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedAdvancementRollsBackScoreAndGames() {
        Fixture fixture = fixture(8);
        try {
            schedules.arrange(fixture.manager(), fixture.competition(), 17L);
            schedules.start(fixture.manager(), fixture.competition());
            var first = bracket(fixture.competition()).getFirst();
            UUID matchId = (UUID) first.get("id");
            UUID nextId = (UUID) first.get("next_match_id");
            String column = "A".equals(first.get("winner_to_slot"))
                ? "player_a_user_id" : "player_b_user_id";
            UUID wrongPlayer = fixture.players().stream()
                .filter(player -> !player.equals(first.get("player_a_user_id"))).findFirst().orElseThrow();
            jdbc.update("update matches set " + column + "=? where id=?", wrongPlayer, nextId);
            matches.start(fixture.principal(), matchId);
            long playingVersion = version(match(matchId));

            var score = new MatchController.ScoreRequest(playingVersion, List.of(
                new MatchController.Game(11, 8), new MatchController.Game(11, 9)));
            assertEquals("MATCH_ADVANCEMENT_CONFLICT", assertThrows(BusinessException.class,
                () -> matches.score(fixture.principal(), matchId, score)).code());
            assertEquals("PLAYING", match(matchId).get("status"));
            assertEquals(playingVersion, version(match(matchId)));
            assertEquals(0, jdbc.queryForObject(
                "select count(*) from match_games where match_id=?", Integer.class, matchId));
            assertEquals(wrongPlayer, match(nextId).get(column));
        } finally {
            jdbc.update("delete from match_games where match_id in (select id from matches where competition_id=?)",
                fixture.competition());
            jdbc.update("delete from matches where competition_id=?", fixture.competition());
            jdbc.update("delete from competition_stages where competition_id=?", fixture.competition());
            jdbc.update("delete from competition_entries where competition_id=?", fixture.competition());
            jdbc.update("delete from competitions where id=?", fixture.competition());
            for (UUID player : fixture.players()) jdbc.update("delete from users where id=?", player);
            jdbc.update("delete from users where id=?", fixture.manager());
        }
    }

    private Fixture fixture(int count) {
        UUID manager = UUID.randomUUID();
        List<UUID> players = new ArrayList<>();
        for (int index = 0; index < count; index++) players.add(UUID.randomUUID());
        for (UUID user : players) {
            jdbc.update("insert into users(id,account,password_hash,status) values(?,?,?,'NORMAL')",
                user, "s3_" + user.toString().substring(0, 28), "test-only-hash");
        }
        jdbc.update("insert into users(id,account,password_hash,status) values(?,?,?,'NORMAL')",
            manager, "s3_" + manager.toString().substring(0, 28), "test-only-hash");
        UUID competition = UUID.randomUUID();
        jdbc.update("""
            insert into competitions(id,title,competition_type,event_type,format,status,registration_scope,
                registration_requires_review,max_participants,best_of,location,creator_user_id)
            values(?,'Sprint 3 淘汰赛集成测试','DAILY','SINGLES','SINGLE_ELIMINATION',
                'ARRANGING','PUBLIC',false,64,3,'测试场地',?)
            """, competition, manager);
        for (UUID user : players) {
            jdbc.update("""
                insert into competition_entries(id,competition_id,user_id,status,registered_at)
                values(?,?,?,'APPROVED',?)
                """, UUID.randomUUID(), competition, user, Timestamp.from(Instant.now()));
        }
        return new Fixture(manager, new AuthPrincipal(manager, "s3_manager"), competition, players);
    }

    private List<Map<String, Object>> bracket(UUID competition) {
        return jdbc.queryForList("select * from matches where competition_id=? order by round_number,match_number",
            competition);
    }

    private Map<String, Object> match(UUID id) {
        return jdbc.queryForMap("select * from matches where id=?", id);
    }

    private static int round(Map<String, Object> match) {
        return ((Number) match.get("round_number")).intValue();
    }

    private static long version(Map<String, Object> match) {
        return ((Number) match.get("version")).longValue();
    }

    private record Fixture(UUID manager, AuthPrincipal principal, UUID competition, List<UUID> players) {}
}
