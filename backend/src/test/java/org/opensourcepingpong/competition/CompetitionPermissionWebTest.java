package org.opensourcepingpong.competition;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.auth.JwtService;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.common.security.SecurityConfiguration;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.rating.RatingSettlementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

/** Task 16: non-admin write requests to competition endpoints must return 403. */
@WebMvcTest(controllers = CompetitionController.class)
@Import(SecurityConfiguration.class)
class CompetitionPermissionWebTest {
    @MockBean JwtService jwtService;
    @MockBean JdbcTemplate jdbc;
    @MockBean AuthorizationService authorization;
    @MockBean ScheduleService schedules;
    @MockBean RatingSettlementService ratings;
    @Autowired MockMvc mvc;

    private final UUID user = UUID.randomUUID();
    private final UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
        new AuthPrincipal(user, "member"), null, List.of());

    @Test
    void ordinaryUserCannotCreateCompetition() throws Exception {
        deny();
        mvc.perform(post("/api/v1/admin/competitions")
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"测试赛事","type":"DAILY","format":"SINGLE_ELIMINATION","scope":"PUBLIC",
                     "review":false,"maxParticipants":8,"bestOf":3,"location":"体育馆",
                     "registrationStart":"2026-11-01T00:00:00Z","registrationEnd":"2026-11-10T00:00:00Z",
                     "startTime":"2026-11-11T00:00:00Z"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void ordinaryUserCannotEditCompetition() throws Exception {
        deny();
        mvc.perform(put("/api/v1/admin/competitions/{id}", UUID.randomUUID())
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"测试赛事","type":"DAILY","format":"SINGLE_ELIMINATION","scope":"PUBLIC",
                     "review":false,"maxParticipants":8,"bestOf":3,"location":"体育馆",
                     "registrationStart":"2026-11-01T00:00:00Z","registrationEnd":"2026-11-10T00:00:00Z",
                     "startTime":"2026-11-11T00:00:00Z"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void ordinaryUserCannotApproveOrRejectEntries() throws Exception {
        deny();
        mvc.perform(post("/api/v1/admin/competition-entries/{id}/approve", UUID.randomUUID())
                .with(authentication(principal)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mvc.perform(post("/api/v1/admin/competition-entries/{id}/reject", UUID.randomUUID())
                .with(authentication(principal)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void unauthenticatedWriteIsRejectedWith401() throws Exception {
        mvc.perform(post("/api/v1/admin/competitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));
        verifyNoInteractions(jdbc);
    }

    private void deny() {
        doThrow(new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "denied"))
            .when(authorization).require(any(), any());
    }
}
