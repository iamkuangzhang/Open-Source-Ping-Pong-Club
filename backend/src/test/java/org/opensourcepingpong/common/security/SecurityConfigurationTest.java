package org.opensourcepingpong.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.opensourcepingpong.auth.AuthService;
import org.opensourcepingpong.auth.JwtService;
import org.opensourcepingpong.user.UserService;
import org.opensourcepingpong.membership.MembershipService;
import org.opensourcepingpong.membership.MemberQrService;
import org.opensourcepingpong.organization.AuthorizationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.opensourcepingpong.competition.ScheduleService;
import org.opensourcepingpong.competition.ScoreSubmissionService;
import org.opensourcepingpong.rating.RatingSettlementService;
import org.opensourcepingpong.registration.RegistrationService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import(SecurityConfiguration.class)
class SecurityConfigurationTest {
    @MockBean JwtService jwtService;
    @MockBean AuthService authService;
    @MockBean UserService userService;
    @MockBean MembershipService membershipService;
    @MockBean MemberQrService memberQrService;
    @MockBean AuthorizationService authorizationService;
    @MockBean JdbcTemplate jdbcTemplate;
    @MockBean ScheduleService scheduleService;
    @MockBean ScoreSubmissionService scoreSubmissionService;
    @MockBean RegistrationService registrationService;
    @MockBean RatingSettlementService ratingSettlementService;
    @Autowired MockMvc mockMvc;

    @Test
    void apiRequiresAuthenticationWithStandardResponse() throws Exception {
        mockMvc.perform(get("/api/v1/example").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));
    }
}
