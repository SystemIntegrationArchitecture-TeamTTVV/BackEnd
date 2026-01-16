package edu.iuh.fit.se.commonservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Filter to bypass authentication for requests coming from Swagger UI
 * This allows testing all endpoints from Swagger without authentication
 * while still requiring authentication for other requests
 */
@Component
@Order(-100)
public class SwaggerBypassFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        // Check if request is from Swagger UI
        String referer = request.getHeader("Referer");
        String origin = request.getHeader("Origin");
        String authHeader = request.getHeader("Authorization");
        String path = request.getRequestURI();
        
        boolean isFromSwagger = false;
        
        // Check Referer header (Swagger UI typically sends this)
        if (referer != null && (referer.contains("/swagger-ui") || referer.contains("swagger-ui.html"))) {
            isFromSwagger = true;
        }
        
        // Check Origin header - if request comes from localhost and is an API call without auth header
        // Likely from Swagger UI for testing
        if (!isFromSwagger && origin != null && 
            (origin.contains("localhost:8081") || origin.contains("localhost:8080")) &&
            path.startsWith("/api/") && !path.contains("/api/auth/") &&
            (authHeader == null || !authHeader.startsWith("Bearer "))) {
            isFromSwagger = true;
        }
        
        // If request is from Swagger and no authentication exists, set a temporary authentication
        if (isFromSwagger && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Set a temporary authentication with USER role to bypass security checks
            PreAuthenticatedAuthenticationToken authToken = new PreAuthenticatedAuthenticationToken(
                    "swagger-user",
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );
            authToken.setAuthenticated(true);
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
        
        chain.doFilter(request, response);
    }
}

