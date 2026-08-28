package com.woodfurni.security;

import com.woodfurni.auth.enums.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT Authentication Filter.
 * Extracts Bearer token from Authorization header, validates it,
 * and sets the SecurityContext if valid.
 *
 * <p>Bug fix (2026-08-20): the filter was previously setting the JWT subject
 * (a plain String) as the {@code Authentication} principal. Every controller in
 * this project binds {@code @AuthenticationPrincipal UserDetails userDetails},
 * which only resolves when the principal is actually a {@code UserDetails}.
 * Result: NPE on the first call site that touched the principal. We now build
 * a {@link User} and assign it as the principal so {@code @AuthenticationPrincipal}
 * resolves correctly across the board.
 *
 * <p>Executes once per request, before the controller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtProvider.validateToken(token)) {
            try {
                String userId = jwtProvider.getUserIdFromToken(token);
                Role role = jwtProvider.getRoleFromToken(token);

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role.name())
                );

                UserDetails principal = User.withUsername(userId)
                        .password("")
                        .authorities(authorities)
                        .build();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Authenticated user: {} with role: {}", userId, role);
            } catch (Exception e) {
                log.error("Cannot set user authentication: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
