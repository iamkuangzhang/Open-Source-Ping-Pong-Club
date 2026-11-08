package org.opensourcepingpong.competition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.opensourcepingpong.rating.RatingSettlementService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementSetter;

class CompetitionControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final ScheduleService schedules = mock(ScheduleService.class);
  private final RatingSettlementService ratings = mock(RatingSettlementService.class);
  private final UUID userId = UUID.randomUUID();
  private final AuthPrincipal principal = new AuthPrincipal(userId, "manager");
  private CompetitionController controller;

  @BeforeEach
  void setUp() {
    controller = new CompetitionController(jdbc, authorization, schedules, ratings);
  }

  @Test
  void createsPingPongRatedCompetitionWithLegacyDefaultSport() {
    UUID sportId = UUID.randomUUID();
    when(jdbc.queryForList("select id from sports where code=? and active=true", "PING_PONG"))
        .thenReturn(List.of(Map.of("id", sportId)));
    when(jdbc.update(anyString(), any(PreparedStatementSetter.class))).thenReturn(1);

    var response = controller.create(principal, request(CompetitionType.RATED, null, 5));

    assertTrue(response.data().containsKey("id"));
    verify(authorization).require(userId, Permission.COMPETITION_CREATE);
    verify(jdbc).update(anyString(), any(PreparedStatementSetter.class));
  }

  @Test
  void deniesCreateWhenCallerCannotManageCompetitions() {
    doThrow(new BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "denied"))
        .when(authorization).require(userId, Permission.COMPETITION_CREATE);

    BusinessException exception = assertThrows(
        BusinessException.class, () -> controller.create(principal, request(CompetitionType.DAILY, "PING_PONG", 3)));

    assertEquals("PERMISSION_DENIED", exception.code());
    verify(jdbc, never()).queryForList(anyString(), any(Object.class));
    verify(jdbc, never()).update(any(PreparedStatementCreator.class));
  }

  @Test
  void rejectsUnsupportedBestOfWithoutQueryingSportCatalog() {
    BusinessException exception = assertThrows(
        BusinessException.class, () -> controller.create(principal, request(CompetitionType.DAILY, "PING_PONG", 4)));

    assertEquals("VALIDATION_ERROR", exception.code());
    verify(jdbc, never()).queryForList(anyString(), any(Object.class));
  }

  @Test
  void rejectsUnknownOrInactiveSport() {
    when(jdbc.queryForList("select id from sports where code=? and active=true", "VOLLEYBALL"))
        .thenReturn(List.of());

    BusinessException exception = assertThrows(BusinessException.class,
        () -> controller.create(principal, request(CompetitionType.DAILY, "volleyball", 3)));

    assertEquals("SPORT_NOT_FOUND", exception.code());
    verify(jdbc, never()).update(any(PreparedStatementCreator.class));
  }

  @Test
  void rejectsRatedCompetitionForNonPingPongSport() {
    when(jdbc.queryForList("select id from sports where code=? and active=true", "BADMINTON"))
        .thenReturn(List.of(Map.of("id", UUID.randomUUID())));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> controller.create(principal, request(CompetitionType.RATED, "badminton", 3)));

    assertEquals("SPORT_RATING_UNSUPPORTED", exception.code());
    verify(jdbc, never()).update(any(PreparedStatementCreator.class));
  }

  @Test
  void competitionDetailReturnsNotFoundWhenIdDoesNotExist() {
    UUID competitionId = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select c.*,s.code"), eq(competitionId))).thenReturn(List.of());

    BusinessException exception = assertThrows(
        BusinessException.class, () -> controller.detail(competitionId));

    assertEquals("COMPETITION_NOT_FOUND", exception.code());
  }

  @Test
  void entryCannotBeCreatedForMissingCompetition() {
    UUID competitionId = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select status,max_participants"), eq(competitionId)))
        .thenReturn(List.of());

    BusinessException exception = assertThrows(
        BusinessException.class, () -> controller.enter(principal, competitionId));

    assertEquals("COMPETITION_NOT_FOUND", exception.code());
  }

  @Test
  void entryCannotBeCreatedOutsideRegistration() {
    UUID competitionId = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select status,max_participants"), eq(competitionId)))
        .thenReturn(List.of(Map.of("status", "ARRANGING", "max_participants", 8, "registration_requires_review", false)));

    BusinessException exception = assertThrows(
        BusinessException.class, () -> controller.enter(principal, competitionId));

    assertEquals("COMPETITION_REGISTRATION_CLOSED", exception.code());
    verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), any());
  }

  @Test
  void entryCannotExceedParticipantLimit() {
    UUID competitionId = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select status,max_participants"), eq(competitionId)))
        .thenReturn(List.of(Map.of("status", "REGISTRATION", "max_participants", 2, "registration_requires_review", false)));
    when(jdbc.queryForObject(startsWith("select count(*)"), eq(Integer.class), eq(competitionId))).thenReturn(2);

    BusinessException exception = assertThrows(
        BusinessException.class, () -> controller.enter(principal, competitionId));

    assertEquals("COMPETITION_FULL", exception.code());
    verify(jdbc, never()).update(any(PreparedStatementCreator.class));
  }

  @Test
  void duplicateEntryMapsDatabaseConflictToBusinessError() {
    UUID competitionId = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select status,max_participants"), eq(competitionId)))
        .thenReturn(List.of(Map.of("status", "REGISTRATION", "max_participants", 8, "registration_requires_review", true)));
    when(jdbc.queryForObject(startsWith("select count(*)"), eq(Integer.class), eq(competitionId))).thenReturn(0);
    when(jdbc.update(anyString(), any(PreparedStatementSetter.class)))
        .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));

    BusinessException exception = assertThrows(
        BusinessException.class, () -> controller.enter(principal, competitionId));

    assertEquals("COMPETITION_ALREADY_REGISTERED", exception.code());
  }

  @Test
  void successfulEntryRequiresReviewWhenCompetitionRequiresIt() {
    UUID competitionId = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select status,max_participants"), eq(competitionId)))
        .thenReturn(List.of(Map.of("status", "REGISTRATION", "max_participants", 8, "registration_requires_review", true)));
    when(jdbc.queryForObject(startsWith("select count(*)"), eq(Integer.class), eq(competitionId))).thenReturn(0);
    when(jdbc.update(anyString(), any(PreparedStatementSetter.class))).thenReturn(1);

    controller.enter(principal, competitionId);

    verify(jdbc).update(anyString(), any(PreparedStatementSetter.class));
  }

  @Test
  void competitionEditOnlyAllowsDraftOrRegistrationStates() {
    UUID competitionId = UUID.randomUUID();
    when(jdbc.queryForList(startsWith("select status from competitions"), eq(competitionId)))
        .thenReturn(List.of(Map.of("status", "RUNNING")));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> controller.update(principal, competitionId, request(CompetitionType.DAILY, "PING_PONG", 3)));

    assertEquals("COMPETITION_INVALID_STATE", exception.code());
    verify(authorization).require(userId, Permission.COMPETITION_MANAGE);
    verify(jdbc, never()).queryForList(startsWith("select id from sports"), any(Object.class));
  }

  @Test
  void arrangeUsesProvidedSeedAndFallsBackToClockSeed() {
    UUID competitionId = UUID.randomUUID();

    controller.arrange(principal, competitionId, Map.of("seed", 2026L));
    controller.arrange(principal, competitionId, null);

    verify(schedules).arrange(userId, competitionId, 2026L);
    verify(schedules).arrange(eq(userId), eq(competitionId), longThat(seed -> seed != 2026L));
  }

  @Test
  void competitionLifecycleCommandsDelegateToAuthorizedServices() {
    UUID competitionId = UUID.randomUUID();

    controller.start(principal, competitionId);
    controller.confirming(principal, competitionId);
    controller.finalizeCompetition(principal, competitionId);

    verify(schedules).start(userId, competitionId);
    verify(schedules).confirming(userId, competitionId);
    verify(ratings).finalizeCompetition(userId, competitionId);
  }

  private static CompetitionController.Create request(CompetitionType type, String sportCode, int bestOf) {
    Instant now = Instant.parse("2026-10-27T00:00:00Z");
    return new CompetitionController.Create(
        "赛事", "说明", type, CompetitionFormat.SINGLE_ELIMINATION, "PUBLIC",
        false, 8, bestOf, "活动地点", now, now.plusSeconds(3600), now.plusSeconds(7200), sportCode);
  }
}
