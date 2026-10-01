package org.opensourcepingpong.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final Algorithm algorithm;
    private final AuthProperties properties;
    public JwtService(AuthProperties properties) { this.properties=properties; this.algorithm=Algorithm.HMAC256(properties.secret()); }
    public String issueAccess(AuthPrincipal principal) { return JWT.create().withSubject(principal.userId().toString()).withClaim("account", principal.account()).withClaim("type","access").withExpiresAt(Date.from(Instant.now().plusSeconds(properties.accessTtlMinutes()*60))).sign(algorithm); }
    public Optional<AuthPrincipal> parseAccess(String token) { try { var jwt=JWT.require(algorithm).build().verify(token); if (!"access".equals(jwt.getClaim("type").asString())) return Optional.empty(); return Optional.of(new AuthPrincipal(UUID.fromString(jwt.getSubject()),jwt.getClaim("account").asString())); } catch (JWTVerificationException | IllegalArgumentException ex) { return Optional.empty(); } }
}
