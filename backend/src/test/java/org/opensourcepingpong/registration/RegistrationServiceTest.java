package org.opensourcepingpong.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensourcepingpong.common.api.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

class RegistrationServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UUID user = UUID.randomUUID();
    private final UUID competition = UUID.randomUUID();
    private final UUID entry = UUID.randomUUID();
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(jdbc);
    }

    @Test
    void fullCompetitionPlacesRegistrationOnWaitlist() {
        givenOpenCompetition(2, 2);
        when(jdbc.queryForObject(startsWith("select count(*) from registration_members"),
            eq(Long.class), eq(competition), eq(user))).thenReturn(0L);
        when(jdbc.queryForObject(startsWith("select count(distinct e.competition_id)"),
            eq(Long.class), eq(user), eq(competition))).thenReturn(0L);
        when(jdbc.update(startsWith("update competitions set filled_slots"), eq(competition))).thenReturn(0);
        when(jdbc.queryForList(startsWith("select id from competitions where id=?"), eq(competition)))
            .thenReturn(List.of(Map.of("id", competition)));
        when(jdbc.queryForObject(startsWith("select coalesce(max(waitlist_no)"),
            eq(Integer.class), eq(competition))).thenReturn(1);
        when(jdbc.update(startsWith("insert into competition_entries"),
            any(PreparedStatementSetter.class))).thenReturn(1);
        when(jdbc.update(startsWith("insert into registration_members"),
            any(), any(), any(), eq("LEADER"))).thenReturn(1);

        var result = service.register(user, competition,
            new RegistrationService.RegisterRequest("INDIVIDUAL", null, null));

        assertEquals("WAITLISTED", result.get("status"));
        assertEquals(1, result.get("waitlistNo"));
        verify(jdbc, never()).update(startsWith("insert into registration_payments"), any(Object[].class));
    }

    @Test
    void lowCreditScoreIsRejectedBeforeTakingASlot() {
        givenOpenCompetition(8, 0);
        when(jdbc.queryForList(startsWith("select score from credit_accounts"), eq(user)))
            .thenReturn(List.of(Map.of("score", 55)));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.register(user, competition,
                new RegistrationService.RegisterRequest("INDIVIDUAL", null, null)));

        assertEquals("REG_CREDIT_TOO_LOW", exception.code());
        verify(jdbc, never()).update(startsWith("update competitions set filled_slots"), eq(competition));
    }

    @Test
    void duplicateRegistrationIsRejected() {
        givenOpenCompetition(8, 0);
        when(jdbc.queryForObject(startsWith("select count(*) from registration_members"),
            eq(Long.class), eq(competition), eq(user))).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.register(user, competition,
                new RegistrationService.RegisterRequest("INDIVIDUAL", null, null)));

        assertEquals("REG_DUPLICATE", exception.code());
        verify(jdbc, never()).update(startsWith("update competitions set filled_slots"), eq(competition));
    }

    @Test
    void withdrawalInsideTwentyFourHoursIsRejected() {
        when(jdbc.queryForList(startsWith("select e.id,e.user_id,e.status,e.payment_status,e.competition_id"),
            eq(entry))).thenReturn(List.of(withdrawalRow(Instant.now().plusSeconds(3600))));

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.withdraw(user, entry));

        assertEquals("REG_WITHDRAW_TOO_LATE", exception.code());
        verify(jdbc, never()).update(startsWith("update competition_entries set status='CANCELLED'"),
            any(), any(), any());
    }

    @Test
    void paymentIsIdempotentAndReturnsExistingCredential() {
        when(jdbc.queryForList(startsWith("select e.id,e.user_id,e.status,e.payment_status"),
            eq(entry))).thenReturn(List.of(Map.of(
                "id", entry,
                "user_id", user,
                "status", "APPROVED",
                "payment_status", "PAID",
                "credential_no", "REG-20261110-ABCDEF12",
                "qr_token", "token-1",
                "fee_cents", 0,
                "registration_requires_review", false)));

        var result = service.pay(user, entry);

        assertEquals("REG-20261110-ABCDEF12", result.get("credentialNo"));
        verify(jdbc, never()).update(startsWith("insert into registration_payments"),
            any(Object[].class));
    }

    @Test
    void withdrawalReleasesSlotAndPromotesFirstWaitlistedEntry() {
        UUID promoted = UUID.randomUUID();
        when(jdbc.queryForList(startsWith("select e.id,e.user_id,e.status,e.payment_status,e.competition_id"),
            eq(entry))).thenReturn(List.of(withdrawalRow(Instant.now().plusSeconds(72 * 3600))));
        when(jdbc.update(startsWith("update competition_entries set status='CANCELLED'"),
            any(), any(), eq(entry))).thenReturn(1);
        when(jdbc.update(startsWith("update competitions set filled_slots=greatest"), eq(competition))).thenReturn(1);
        when(jdbc.queryForList(startsWith("select id,user_id from competition_entries"), eq(competition)))
            .thenReturn(List.of(Map.of("id", promoted, "user_id", UUID.randomUUID())));
        when(jdbc.update(startsWith("update competition_entries set status=?"),
            anyString(), eq(promoted))).thenReturn(1);
        when(jdbc.update(startsWith("update competitions set filled_slots=filled_slots+1"),
            eq(competition))).thenReturn(1);
        when(jdbc.update(startsWith("insert into notifications"),
            any(), any(), any(), any(), any())).thenReturn(1);

        var result = service.withdraw(user, entry);

        assertEquals("CANCELLED", result.get("status"));
        assertEquals("REFUNDED", result.get("refundStatus"));
        assertEquals(promoted, result.get("promotedEntryId"));
    }

    private void givenOpenCompetition(int maxParticipants, int filledSlots) {
        when(jdbc.queryForList(startsWith("select id,status,registration_start"), eq(competition)))
            .thenReturn(List.of(Map.of(
                "id", competition,
                "status", "REGISTRATION",
                "registration_start", Timestamp.from(Instant.now().minusSeconds(3600)),
                "registration_end", Timestamp.from(Instant.now().plusSeconds(86400)),
                "registration_scope", "PUBLIC",
                "registration_requires_review", false,
                "max_participants", maxParticipants,
                "filled_slots", filledSlots)));
        when(jdbc.queryForList(startsWith("select status from users"), eq(user)))
            .thenReturn(List.of(Map.of("status", "NORMAL")));
        when(jdbc.queryForList(startsWith("select score from credit_accounts"), eq(user)))
            .thenReturn(List.of());
    }

    private Map<String, Object> withdrawalRow(Instant startTime) {
        return Map.of(
            "id", entry,
            "user_id", user,
            "status", "APPROVED",
            "payment_status", "PAID",
            "competition_id", competition,
            "start_time", Timestamp.from(startTime),
            "registration_requires_review", false);
    }
}
