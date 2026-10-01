package org.opensourcepingpong.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthDtosTest {
  @Test
  void authenticationDtosNeverExposePasswordsOrTokensThroughToString() {
    String password = "sensitive-password";
    String accessToken = "sensitive-access-token";
    String refreshToken = "sensitive-refresh-token";

    String output = String.join(" ",
        new AuthDtos.RegisterRequest("user", password, "User").toString(),
        new AuthDtos.LoginRequest("user", password).toString(),
        new AuthDtos.RefreshRequest(refreshToken).toString(),
        new AuthDtos.TokenResponse(accessToken, refreshToken, 900).toString());

    assertFalse(output.contains(password));
    assertFalse(output.contains(accessToken));
    assertFalse(output.contains(refreshToken));
    assertTrue(output.contains("[REDACTED]"));
  }
}
