package org.opensourcepingpong.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.user.domain.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
  private final UserRepository users = mock(UserRepository.class);
  private final UserProfileRepository profiles = mock(UserProfileRepository.class);
  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> values = mock(ValueOperations.class);
  private final JwtService jwt = mock(JwtService.class);
  private AuthService service;

  @BeforeEach
  void setUp() {
    when(redis.opsForValue()).thenReturn(values);
    when(jwt.issueAccess(any())).thenReturn("access-token");
    service = new AuthService(users, profiles, passwordEncoder, redis, jwt, new AuthProperties("x".repeat(64), 15, 30));
  }

  @Test
  void registerCreatesNormalUserWithBCryptPasswordAndRefreshToken() {
    when(users.existsByAccount("alice")).thenReturn(false);
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var tokens = service.register(new AuthDtos.RegisterRequest(" Alice ", "correct-password", "Alice"));

    assertEquals("access-token", tokens.accessToken());
    verify(users).save(argThat(user -> user.getAccount().equals("alice")
        && user.getStatus() == UserStatus.NORMAL
        && passwordEncoder.matches("correct-password", user.getPasswordHash())
        && !user.getPasswordHash().equals("correct-password")));
    verify(profiles).save(any(UserProfile.class));
    verify(values).set(startsWith("auth:refresh:"), anyString(), eq(Duration.ofDays(30)));
  }

  @Test
  void duplicateAccountCannotRegister() {
    when(users.existsByAccount("alice")).thenReturn(true);

    var ex = assertThrows(BusinessException.class,
        () -> service.register(new AuthDtos.RegisterRequest("alice", "correct-password", "Alice")));

    assertEquals("AUTH_ACCOUNT_EXISTS", ex.code());
    verify(users, never()).save(any());
  }

  @Test
  void loginAcceptsCorrectPasswordAndRejectsWrongPassword() {
    User user = new User("alice", passwordEncoder.encode("correct-password"));
    when(users.findByAccount("alice")).thenReturn(Optional.of(user));

    assertEquals("access-token", service.login(new AuthDtos.LoginRequest("Alice", "correct-password")).accessToken());

    var ex = assertThrows(BusinessException.class,
        () -> service.login(new AuthDtos.LoginRequest("alice", "wrong-password")));
    assertEquals("AUTH_INVALID_CREDENTIALS", ex.code());
  }

  @Test
  void disabledUserCannotLoginOrRefresh() throws Exception {
    User user = new User("alice", passwordEncoder.encode("correct-password"));
    setStatus(user, UserStatus.DISABLED);
    when(users.findByAccount("alice")).thenReturn(Optional.of(user));
    when(values.get(anyString())).thenReturn(user.getId().toString());
    when(users.findById(user.getId())).thenReturn(Optional.of(user));

    assertEquals("AUTH_ACCOUNT_DISABLED", assertThrows(BusinessException.class,
        () -> service.login(new AuthDtos.LoginRequest("alice", "correct-password"))).code());
    assertEquals("AUTH_ACCOUNT_DISABLED", assertThrows(BusinessException.class,
        () -> service.refresh("refresh-token")).code());
  }

  @Test
  void refreshRotatesStoredTokenAndLogoutInvalidatesIt() {
    User user = new User("alice", passwordEncoder.encode("correct-password"));
    UUID userId = user.getId();
    when(values.get(anyString())).thenReturn(userId.toString());
    when(users.findById(userId)).thenReturn(Optional.of(user));

    var tokens = service.refresh("old-refresh-token");
    service.logout(tokens.refreshToken());

    assertEquals("access-token", tokens.accessToken());
    verify(redis, atLeastOnce()).delete(startsWith("auth:refresh:"));
    verify(values).set(startsWith("auth:refresh:"), eq(userId.toString()), eq(Duration.ofDays(30)));
  }

  @Test
  void missingRefreshTokenIsRejected() {
    when(values.get(anyString())).thenReturn(null);

    var ex = assertThrows(BusinessException.class, () -> service.refresh("missing"));

    assertEquals("AUTH_TOKEN_EXPIRED", ex.code());
  }

  private static void setStatus(User user, UserStatus status) throws Exception {
    Field field = User.class.getDeclaredField("status");
    field.setAccessible(true);
    field.set(user, status);
  }
}
