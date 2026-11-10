package org.opensourcepingpong.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opensourcepingpong.common.api.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health","/api/v1/auth/**","/api/v1/users/*/public","/api/v1/public/**").permitAll().requestMatchers(HttpMethod.GET,"/api/v1/competitions/**","/api/v1/events/*/bracket","/api/v1/matches/*","/api/v1/announcements/**","/api/v1/play-posts/**","/api/v1/meetups/**","/api/v1/market-posts/**","/api/v1/marketplace/**","/api/v1/forum/posts","/api/v1/forum/posts/*","/api/v1/forum/posts/*/comments","/api/v1/ratings/ranking","/api/v1/ratings/leaderboard","/api/v1/ratings/users/**","/api/v1/ratings/*/history").permitAll().requestMatchers("/api/v1/**").authenticated().anyRequest().denyAll())
                .exceptionHandling(handler -> handler.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(response.getOutputStream(), ApiResponse.failure("AUTH_TOKEN_EXPIRED", "未登录或令牌无效"));
                }).accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(response.getOutputStream(), ApiResponse.failure("PERMISSION_DENIED", "无权访问该资源"));
                })).addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
