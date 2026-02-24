package com.dbs.symphony.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Dev/test-only filter. When app.security.enabled=false, reads the X-User-Id request header
 * and sets it as the authenticated principal so callers can simulate different users (user1, user2, …).
 * If the header is absent the anonymous principal configured in SecurityConfig is used instead.
 */
public class DevUserHeaderFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userId = request.getHeader(HEADER);
        if (userId != null && !userId.isBlank()) {
            var authorities = List.of(
                    new SimpleGrantedAuthority("workbench.user"),
                    new SimpleGrantedAuthority("workbench.manager")
            );
            var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}
