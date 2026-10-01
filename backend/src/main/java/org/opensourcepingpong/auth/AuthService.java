package org.opensourcepingpong.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.user.domain.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users; private final UserProfileRepository profiles; private final PasswordEncoder passwords; private final StringRedisTemplate redis; private final JwtService jwt; private final AuthProperties properties;
    public AuthService(UserRepository users,UserProfileRepository profiles,PasswordEncoder passwords,StringRedisTemplate redis,JwtService jwt,AuthProperties properties){this.users=users;this.profiles=profiles;this.passwords=passwords;this.redis=redis;this.jwt=jwt;this.properties=properties;}
    @Transactional public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest request){String account=request.account().trim().toLowerCase(Locale.ROOT);if(users.existsByAccount(account))throw new BusinessException(HttpStatus.CONFLICT,"AUTH_ACCOUNT_EXISTS","账号已存在");User user=users.save(new User(account,passwords.encode(request.password())));profiles.save(new UserProfile(user.getId(),request.nickname().trim()));return tokens(user);}
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request){User user=users.findByAccount(request.account().trim().toLowerCase(Locale.ROOT)).orElseThrow(()->invalid());if(!passwords.matches(request.password(),user.getPasswordHash()))throw invalid();if(user.getStatus()==UserStatus.DISABLED)throw new BusinessException(HttpStatus.UNAUTHORIZED,"AUTH_ACCOUNT_DISABLED","账号已禁用");return tokens(user);}
    public AuthDtos.TokenResponse refresh(String token){String key=refreshKey(token);String userId=redis.opsForValue().get(key);if(userId==null)throw new BusinessException(HttpStatus.UNAUTHORIZED,"AUTH_TOKEN_EXPIRED","Refresh Token 已失效");redis.delete(key);User user=users.findById(UUID.fromString(userId)).orElseThrow(()->new BusinessException(HttpStatus.UNAUTHORIZED,"AUTH_TOKEN_EXPIRED","Refresh Token 已失效"));if(user.getStatus()!=UserStatus.NORMAL)throw new BusinessException(HttpStatus.UNAUTHORIZED,"AUTH_ACCOUNT_DISABLED","账号已禁用");return tokens(user);}
    public void logout(String token){redis.delete(refreshKey(token));}
    private AuthDtos.TokenResponse tokens(User user){String refresh=randomToken();redis.opsForValue().set(refreshKey(refresh),user.getId().toString(),Duration.ofDays(properties.refreshTtlDays()));return new AuthDtos.TokenResponse(jwt.issueAccess(new AuthPrincipal(user.getId(),user.getAccount())),refresh,properties.accessTtlMinutes()*60);}
    private static BusinessException invalid(){return new BusinessException(HttpStatus.UNAUTHORIZED,"AUTH_INVALID_CREDENTIALS","账号或密码错误");}
    private static String randomToken(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static String refreshKey(String token){try{return "auth:refresh:"+Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
