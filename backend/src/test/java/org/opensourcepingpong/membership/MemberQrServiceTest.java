package org.opensourcepingpong.membership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.RoleCode;
import org.opensourcepingpong.user.UserService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MemberQrServiceTest {
  private final MembershipRepository memberships = mock(MembershipRepository.class);
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> values = mock(ValueOperations.class);
  private final AuthorizationService roles = mock(AuthorizationService.class);
  private final UserService users = mock(UserService.class);
  private MemberQrService service;

  @BeforeEach
  void setUp() {
    when(redis.opsForValue()).thenReturn(values);
    service = new MemberQrService(memberships, redis, roles, users);
  }

  @Test
  void activeMemberCanGenerateQrTokenWithSixtySecondTtl() {
    UUID user = UUID.randomUUID();
    when(memberships.findByUserId(user)).thenReturn(Optional.of(new Membership(user, "OSC-0001")));

    var qr = service.generate(user);

    assertEquals(60, qr.expiresIn());
    assertFalse(qr.token().contains(user.toString()));
    verify(values).set(startsWith("member:qr:"), eq(user.toString()), eq(Duration.ofSeconds(60)));
  }

  @Test
  void ordinaryUserCannotGenerateQrToken() {
    UUID user = UUID.randomUUID();
    when(memberships.findByUserId(user)).thenReturn(Optional.empty());

    var ex = assertThrows(BusinessException.class, () -> service.generate(user));

    assertEquals("QR_TOKEN_INVALID", ex.code());
  }

  @Test
  void expiredQrTokenIsRejected() {
    when(values.get(startsWith("member:qr:"))).thenReturn(null);

    var ex = assertThrows(BusinessException.class, () -> service.verify("expired-token"));

    assertEquals("QR_TOKEN_EXPIRED", ex.code());
  }

  @Test
  void inactiveMemberTokenNoLongerVerifies() throws Exception {
    UUID user = UUID.randomUUID();
    Membership membership = new Membership(user, "OSC-0001");
    setStatus(membership, MembershipStatus.REMOVED);
    when(values.get(anyString())).thenReturn(user.toString());
    when(memberships.findByUserId(user)).thenReturn(Optional.of(membership));

    var ex = assertThrows(BusinessException.class, () -> service.verify("token"));

    assertEquals("QR_TOKEN_INVALID", ex.code());
  }

  @Test
  void verificationReturnsOnlyPublicMemberIdentity() {
    UUID user = UUID.randomUUID();
    when(values.get(anyString())).thenReturn(user.toString());
    when(memberships.findByUserId(user)).thenReturn(Optional.of(new Membership(user, "OSC-0001")));
    when(users.publicProfile(user)).thenReturn(new UserService.PublicUserView(user, "nick", "张三", "avatar"));
    when(roles.roles(user)).thenReturn(Set.of(RoleCode.MEMBER));

    var verification = service.verify("token");

    assertEquals("张三", verification.name());
    assertEquals("OSC-0001", verification.memberNumber());
    assertEquals("MEMBER", verification.role());
    assertEquals("ACTIVE", verification.status());
  }

  @Test
  void verificationFallsBackToNicknameWhenRealNameIsUnavailable() {
    UUID user = UUID.randomUUID();
    when(values.get(anyString())).thenReturn(user.toString());
    when(memberships.findByUserId(user)).thenReturn(Optional.of(new Membership(user, "OSC-0002")));
    when(users.publicProfile(user)).thenReturn(new UserService.PublicUserView(user, "昵称", null, "avatar"));
    when(roles.roles(user)).thenReturn(Set.of(RoleCode.MEMBER, RoleCode.ADMIN));

    var verification = service.verify("token");

    assertEquals("昵称", verification.name());
    assertTrue(Set.of("MEMBER", "ADMIN").contains(verification.role()));
  }

  private static void setStatus(Membership membership, MembershipStatus status) throws Exception {
    Field field = Membership.class.getDeclaredField("status");
    field.setAccessible(true);
    field.set(membership, status);
  }
}
