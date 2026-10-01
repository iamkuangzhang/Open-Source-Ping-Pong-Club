package org.opensourcepingpong.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record AuthProperties(String secret, long accessTtlMinutes, long refreshTtlDays) {}
