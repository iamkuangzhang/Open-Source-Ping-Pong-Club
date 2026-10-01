package org.opensourcepingpong.auth;
import java.util.UUID;
public record AuthPrincipal(UUID userId, String account) {}
