package org.opensourcepingpong.competition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

class ScoreSubmissionServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final ScheduleService schedules = mock(ScheduleService.class);
    private final UUID operator = UUID.randomUUID();
    private final UUID match = UUID.randomUUID();
    private final UUID playerA = UUID.randomUUID();
    private final UUID playerB = UUID.randomUUID();
    private ScoreSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new ScoreSubmissionService(jdbc, authorization, schedules, new ObjectMapper());
    }

    @Test
    void invalidTableTennisScoreIsRejectedBeforeSettlement() {
        givenMatch("PLAYING", "NORMAL", 1, null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.submit(
            operator, match,
            new ScoreSubmissionService.ScoreRequest(1,
                List.of(new ScoreSubmissionService.Game(11, 10), new ScoreSubmissionService.Game(11, 8)),
                null),
            null));

        assertEquals("MATCH_INVALID_SCORE", exception.code());
        verify(authorization).require(operator, Permission.MATCH_SCORE_EDIT);
        verify(schedules, never()).advance(any(), any());
    }

    @Test
    void validScoreCompletesMatchAndAdvancesWinner() {
        givenMatch("PLAYING", "NORMAL", 1, null);
        when(jdbc.update(startsWith("update matches set status='COMPLETED'"),
            any(), any(), anyInt(), anyInt(), any(), any(), any(), eq(match), eq(1L))).thenReturn(1);
        when(jdbc.update(startsWith("delete from match_games"), eq(match))).thenReturn(1);
        when(jdbc.update(startsWith("insert into match_games"), any(), eq(match), anyInt(), anyInt(), anyInt(), any()))
            .thenReturn(1);
        when(jdbc.update(startsWith("insert into match_score_logs"), any(PreparedStatementSetter.class)))
            .thenReturn(1);
        when(jdbc.update(startsWith("insert into audit_logs"),
            any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        var result = service.submit(operator, match,
            new ScoreSubmissionService.ScoreRequest(1,
                List.of(new ScoreSubmissionService.Game(11, 8), new ScoreSubmissionService.Game(11, 9)),
                null),
            "idem-1");

        assertEquals("COMPLETED", result.get("status"));
        assertEquals(playerA, result.get("winnerUserId"));
        verify(schedules).advance(match, playerA);
    }

    @Test
    void staleVersionIsRejected() {
        givenMatch("COMPLETED", "NORMAL", 2, Timestamp.from(Instant.now().plusSeconds(600)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.edit(
            operator, match,
            new ScoreSubmissionService.ScoreRequest(1,
                List.of(new ScoreSubmissionService.Game(11, 9), new ScoreSubmissionService.Game(11, 8)),
                "fix")));

        assertEquals("MATCH_VERSION_CONFLICT", exception.code());
        verify(jdbc, never()).update(startsWith("insert into match_score_change_requests"), any(Object[].class));
    }

    @Test
    void lateEditCreatesApprovalRequestInsteadOfChangingResult() {
        givenMatch("COMPLETED", "NORMAL", 1, Timestamp.from(Instant.now().minusSeconds(60)));
        when(jdbc.queryForList(startsWith("select game_number as"), eq(match)))
            .thenReturn(List.of(Map.of("gameNumber", 1, "a", 11, "b", 8)));
        when(jdbc.update(startsWith("insert into match_score_change_requests"),
            any(), eq(match), eq(operator), eq(1L), anyString(), anyString(), anyString())).thenReturn(1);

        var result = service.edit(operator, match,
            new ScoreSubmissionService.ScoreRequest(1,
                List.of(new ScoreSubmissionService.Game(11, 9), new ScoreSubmissionService.Game(11, 8)),
                "裁判记错分数"));

        assertEquals("PENDING_APPROVAL", result.get("status"));
        verify(jdbc, never()).update(startsWith("update matches set winner_user_id"),
            any(Object[].class));
    }

    @Test
    void walkoverCompletesMatchWithoutRatingAndAdvancesWinner() {
        givenMatch("READY", "NORMAL", 1, null);
        when(jdbc.update(startsWith("update matches set status='COMPLETED',result_type='WALKOVER'"),
            any(), any(), anyString(), any(), eq(match), eq(1L))).thenReturn(1);
        when(jdbc.update(startsWith("delete from match_games"), eq(match))).thenReturn(1);
        when(jdbc.update(startsWith("insert into match_score_logs"), any(PreparedStatementSetter.class)))
            .thenReturn(1);
        when(jdbc.update(startsWith("insert into audit_logs"),
            any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        var result = service.walkover(operator, match,
            new ScoreSubmissionService.ResultRequest(playerB, 1, "对方未到场"));

        assertEquals("COMPLETED", result.get("status"));
        assertEquals(playerB, result.get("winnerUserId"));
        verify(schedules).advance(match, playerB);
    }

    private void givenMatch(String status, String resultType, long version, Timestamp deadline) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", match);
        row.put("status", status);
        row.put("result_type", resultType);
        row.put("version", version);
        row.put("best_of", 3);
        row.put("sport", "TABLE_TENNIS");
        row.put("player_a_user_id", playerA);
        row.put("player_b_user_id", playerB);
        row.put("winner_user_id", status.equals("COMPLETED") ? playerA : null);
        row.put("next_match_id", null);
        row.put("winner_to_slot", null);
        row.put("score_edit_deadline", deadline);
        when(jdbc.queryForList(startsWith("select m.*,c.sport"), eq(match))).thenReturn(List.of(row));
    }
}
