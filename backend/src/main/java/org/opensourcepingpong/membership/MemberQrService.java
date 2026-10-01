package org.opensourcepingpong.membership;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.RoleCode;
import org.opensourcepingpong.user.UserService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MemberQrService {
    private final MembershipRepository memberships; private final StringRedisTemplate redis; private final AuthorizationService roles; private final UserService users;
    public MemberQrService(MembershipRepository memberships, StringRedisTemplate redis, AuthorizationService roles, UserService users) { this.memberships=memberships; this.redis=redis; this.roles=roles; this.users=users; }
    public QrToken generate(UUID user) { active(user); byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); redis.opsForValue().set(key(token),user.toString(),Duration.ofSeconds(60)); return new QrToken(token,60); }
    public Verification verify(String token) { String userId=redis.opsForValue().get(key(token)); if(userId==null) throw new BusinessException(HttpStatus.NOT_FOUND,"QR_TOKEN_EXPIRED","二维码已过期"); UUID id=UUID.fromString(userId); Membership member=active(id); var profile=users.publicProfile(id); String role=roles.roles(id).stream().filter(r->r!=RoleCode.MEMBER).findFirst().orElse(RoleCode.MEMBER).name(); return new Verification(profile.realName()!=null?profile.realName():profile.nickname(),member.getMemberNumber(),role,member.getStatus().name(),Instant.now()); }
    private Membership active(UUID id) { Membership m=memberships.findByUserId(id).orElseThrow(()->new BusinessException(HttpStatus.FORBIDDEN,"QR_TOKEN_INVALID","不是正式成员")); if(m.getStatus()!=MembershipStatus.ACTIVE) throw new BusinessException(HttpStatus.FORBIDDEN,"QR_TOKEN_INVALID","成员状态无效"); return m; }
    private static String key(String token) { try{return "member:qr:"+Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);} }
    public record QrToken(String token,long expiresIn) {} public record Verification(String name,String memberNumber,String role,String status,Instant verifiedAt) {}
}
