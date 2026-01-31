package edu.iuh.fit.se.commonservice.security;

import edu.iuh.fit.se.commonservice.util.JwtUtil;
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

@Component
@Order(-50)
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.substring(7);
            final String username = jwtUtil.extractUsername(jwt);
            final String role = jwtUtil.extractRole(jwt);

            logger.debug("JWT Filter: username=" + username + ", role=" + role);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtUtil.validateToken(jwt, username)) {
                    String authority = "ROLE_" + role;
                    logger.debug("JWT Filter: Setting authentication with authority=" + authority);
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority(authority))
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    logger.debug("JWT Filter: Authentication set successfully");
                } else {
                    logger.warn("JWT Filter: Token validation failed for username=" + username);
                }
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // Token expired - this is normal, just log as debug/warn, not error
            logger.debug("JWT Filter: Token expired for request: " + request.getRequestURI());
        } catch (io.jsonwebtoken.JwtException e) {
            // Other JWT exceptions (malformed, invalid signature, etc.)
            logger.warn("JWT Filter: Invalid JWT token: " + e.getMessage());
        } catch (Exception e) {
            // Other unexpected errors
            logger.error("JWT Filter: Unexpected error during authentication: " + e.getMessage(), e);
        }

        chain.doFilter(request, response);
    }
}

