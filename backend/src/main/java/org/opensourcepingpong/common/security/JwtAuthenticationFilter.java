package org.opensourcepingpong.common.security;

import jakarta.servlet.*; import jakarta.servlet.http.*;
import org.opensourcepingpong.auth.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt; public JwtAuthenticationFilter(JwtService jwt){this.jwt=jwt;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,java.io.IOException {String h=request.getHeader("Authorization");if(h!=null&&h.startsWith("Bearer "))jwt.parseAccess(h.substring(7)).ifPresent(p->SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,java.util.List.of())));chain.doFilter(request,response);}
}
