package org.opensourcepingpong.membership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.*;
import org.springframework.jdbc.core.JdbcTemplate;

class MembershipServiceTest {
  private final MembershipApplicationRepository applications = mock(MembershipApplicationRepository.class);
  private final MembershipRepository memberships = mock(MembershipRepository.class);
  private final RatingAccountRepository accounts = mock(RatingAccountRepository.class);
  private final RatingLedgerRepository ledgers = mock(RatingLedgerRepository.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final OrganizationRoleRepository roles = mock(OrganizationRoleRepository.class);
  private final UserOrganizationRoleRepository userRoles = mock(UserOrganizationRoleRepository.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private MembershipService service;

  @BeforeEach
  void setUp() {
    service = new MembershipService(applications, memberships, accounts, ledgers, authorization, roles, userRoles, jdbc);
    when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(memberships.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(ledgers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void ordinaryUserCanSubmitOnePendingApplication() {
    UUID user = UUID.randomUUID();
    when(memberships.existsByUserId(user)).thenReturn(false);
    when(applications.existsByUserIdAndStatus(user, ApplicationStatus.PENDING)).thenReturn(false);

    var view = service.apply(user, request());

    assertEquals(user, view.userId());
    assertEquals("PENDING", view.status());
    verify(applications).save(any(MembershipApplication.class));
  }

  @Test
  void activeMemberAndDuplicatePendingApplicationAreRejected() {
    UUID user = UUID.randomUUID();
    when(memberships.existsByUserId(user)).thenReturn(true);
    assertEquals("MEMBERSHIP_ALREADY_ACTIVE", assertThrows(BusinessException.class,
        () -> service.apply(user, request())).code());

    reset(memberships, applications);
    when(memberships.existsByUserId(user)).thenReturn(false);
    when(applications.existsByUserIdAndStatus(user, ApplicationStatus.PENDING)).thenReturn(true);
    assertEquals("MEMBERSHIP_APPLICATION_EXISTS", assertThrows(BusinessException.class,
        () -> service.apply(user, request())).code());
  }

  @Test
  void approvingApplicationCreatesActiveMembershipInitialRatingLedgerAndMemberRole() throws Exception {
    UUID reviewer = UUID.randomUUID();
    UUID applicant = UUID.randomUUID();
    UUID appId = UUID.randomUUID();
    UUID memberRoleId = UUID.randomUUID();
    MembershipApplication application = new MembershipApplication(applicant, "张三", "20260001", "计院", "软件", "2026", "13000000000", "join");
    setField(application, "id", appId);
    OrganizationRole memberRole = mock(OrganizationRole.class);
    when(memberRole.getId()).thenReturn(memberRoleId);
    when(applications.findById(appId)).thenReturn(Optional.of(application));
    when(roles.findByCode(RoleCode.MEMBER)).thenReturn(Optional.of(memberRole));

    service.review(reviewer, appId, true, "ok");

    assertEquals(ApplicationStatus.APPROVED, application.getStatus());
    verify(authorization).require(reviewer, Permission.MEMBERSHIP_REVIEW);
    verify(memberships).save(argThat(m -> m.getUserId().equals(applicant) && m.getStatus() == MembershipStatus.ACTIVE));
    verify(accounts).save(argThat(account -> account.getCurrentRating() == 1400));
    ArgumentCaptor<RatingLedger> ledgerCaptor = ArgumentCaptor.forClass(RatingLedger.class);
    verify(ledgers).save(ledgerCaptor.capture());
    assertEquals("INITIAL", field(ledgerCaptor.getValue(), "sourceType"));
    assertEquals(1400, field(ledgerCaptor.getValue(), "ratingAfter"));
    verify(userRoles).save(argThat(role -> role.getUserId().equals(applicant) && role.getRoleId().equals(memberRoleId)));
    verify(jdbc).update(startsWith("insert into notifications"), any(UUID.class), eq(applicant),
        eq("MEMBERSHIP_REVIEW"), eq("入会申请已通过"), eq("入会申请已通过：ok"));
  }

  @Test
  void duplicateReviewIsRejectedAndDoesNotCreateMemberData() {
    UUID reviewer = UUID.randomUUID();
    UUID appId = UUID.randomUUID();
    MembershipApplication application = new MembershipApplication(UUID.randomUUID(), "张三", "20260001", "计院", "软件", "2026", "13000000000", null);
    application.review(reviewer, true, "ok");
    when(applications.findById(appId)).thenReturn(Optional.of(application));

    var ex = assertThrows(BusinessException.class, () -> service.review(reviewer, appId, true, "again"));

    assertEquals("MEMBERSHIP_APPLICATION_ALREADY_REVIEWED", ex.code());
    verify(memberships, never()).save(any());
    verify(accounts, never()).save(any());
    verify(ledgers, never()).save(any());
  }

  @Test
  void rejectingApplicationDoesNotCreateMembershipOrRatingAndSendsComment() throws Exception {
    UUID reviewer = UUID.randomUUID();
    UUID applicant = UUID.randomUUID();
    UUID appId = UUID.randomUUID();
    MembershipApplication application = new MembershipApplication(
        applicant, "张三", "20260001", "计院", "软件", "2026", "13000000000", null);
    setField(application, "id", appId);
    when(applications.findById(appId)).thenReturn(Optional.of(application));

    service.review(reviewer, appId, false, "资料不完整");

    assertEquals(ApplicationStatus.REJECTED, application.getStatus());
    verify(memberships, never()).save(any());
    verify(accounts, never()).save(any());
    verify(ledgers, never()).save(any());
    verify(userRoles, never()).save(any());
    verify(jdbc).update(startsWith("insert into notifications"), any(UUID.class), eq(applicant),
        eq("MEMBERSHIP_REVIEW"), eq("入会申请未通过"), eq("入会申请未通过：资料不完整"));
  }

  @Test
  void reviewingUnknownApplicationReturnsNotFound() {
    UUID reviewer = UUID.randomUUID();
    UUID appId = UUID.randomUUID();
    when(applications.findById(appId)).thenReturn(Optional.empty());

    var exception = assertThrows(BusinessException.class,
        () -> service.review(reviewer, appId, true, null));

    assertEquals("MEMBERSHIP_APPLICATION_NOT_FOUND", exception.code());
    verify(memberships, never()).save(any());
  }

  @Test
  void unauthorizedReviewerCannotReadOrReviewApplications() {
    UUID reviewer = UUID.randomUUID();
    doThrow(new BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "denied"))
        .when(authorization).require(reviewer, Permission.MEMBERSHIP_REVIEW);

    assertEquals("PERMISSION_DENIED", assertThrows(BusinessException.class, () -> service.pending(reviewer)).code());
    assertEquals("PERMISSION_DENIED", assertThrows(BusinessException.class,
        () -> service.review(reviewer, UUID.randomUUID(), true, "ok")).code());
  }

  private static MembershipService.ApplyRequest request() {
    return new MembershipService.ApplyRequest("张三", "20260001", "计院", "软件工程", "2026", "13000000000", "想加入开源乓协");
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Object field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }
}
