package edu.iuh.fit.se.messegeservice.security;

import edu.iuh.fit.se.messegeservice.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Authentication filter that supports two modes:
 * 1. Gateway-forwarded headers (preferred): reads trusted X-Username, X-Role, X-User-Id
 *    headers injected by API Gateway after JWT validation.
 * 2. Direct JWT fallback: parses Bearer token when request doesn't come through Gateway
 *    (e.g., Swagger UI, inter-service calls).
 */
@Component
@Order(-50)
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        // ── Mode 1: Trust Gateway headers (request already validated by API Gateway) ──
        String gatewayUsername = request.getHeader("X-Username");
        String gatewayRole = request.getHeader("X-Role");

        if (gatewayUsername != null && !gatewayUsername.isBlank()) {
            String authority = "ROLE_" + (gatewayRole != null && !gatewayRole.isBlank() ? gatewayRole : "USER");
            logger.debug("Gateway auth: username=" + gatewayUsername + ", authority=" + authority);

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    gatewayUsername,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority(authority))
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            chain.doFilter(request, response);
            return;
        }

        // ── Mode 2: Fallback — parse JWT directly (Swagger, direct calls) ──
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.substring(7);
            final String username = jwtUtil.extractUsername(jwt);
            final String role = jwtUtil.extractRole(jwt);

            logger.debug("JWT fallback: username=" + username + ", role=" + role);

            if (username != null) {
                if (jwtUtil.validateToken(jwt, username)) {
                    String authority = "ROLE_" + role;
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority(authority))
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    logger.warn("JWT fallback: Token validation failed for username=" + username);
                }
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            logger.debug("JWT fallback: Token expired for request: " + request.getRequestURI());
        } catch (io.jsonwebtoken.JwtException e) {
            logger.warn("JWT fallback: Invalid JWT token: " + e.getMessage());
        } catch (Exception e) {
            logger.error("JWT fallback: Unexpected error: " + e.getMessage(), e);
        }

        chain.doFilter(request, response);
    }
}





