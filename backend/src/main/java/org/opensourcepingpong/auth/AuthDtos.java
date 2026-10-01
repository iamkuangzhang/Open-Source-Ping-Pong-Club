package org.opensourcepingpong.auth;

import jakarta.validation.constraints.*;

public final class AuthDtos {
    private AuthDtos() {}
    public record RegisterRequest(@NotBlank @Pattern(regexp="^[A-Za-z0-9_]{3,32}$") String account, @NotBlank @Size(min=8,max=72) String password, @NotBlank @Size(max=40) String nickname) {
        @Override public String toString() { return "RegisterRequest[account="+account+", password=[REDACTED], nickname="+nickname+"]"; }
    }
    public record LoginRequest(@NotBlank String account, @NotBlank String password) {
        @Override public String toString() { return "LoginRequest[account="+account+", password=[REDACTED]]"; }
    }
    public record RefreshRequest(@NotBlank String refreshToken) {
        @Override public String toString() { return "RefreshRequest[refreshToken=[REDACTED]]"; }
    }
    public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
        @Override public String toString() { return "TokenResponse[accessToken=[REDACTED], refreshToken=[REDACTED], expiresIn="+expiresIn+"]"; }
    }
}
