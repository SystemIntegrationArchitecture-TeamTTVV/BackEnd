package edu.iuh.fit.se.apigateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import edu.iuh.fit.se.apigateway.security.JwtTokenVerifier;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Configuration
public class GatewayConfig {

    @Value("${app.security.enabled:false}")
    private boolean securityEnabled;

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/swagger-ui",
            "/v3/api-docs",
            "/actuator",
            "/fallback",
            // Auth endpoints — must be public (login, register, refresh, forgot/reset password)
            "/api/auth/",
            "/api/users/",
            "/api/common/auth",
            // Swagger / API docs
            "/api/common/v3/api-docs",
            "/api/social/v3/api-docs",
            "/api/message/v3/api-docs",
            "/api/auth-svc/v3/api-docs",
            // WebSocket — also handled in isWsPath block with JWT extraction
            "/api/common/ws",
            "/api/social/ws"
    );

    private boolean isPublicGetPath(String method, String path) {
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return false;
        }

        String normalizedPath = path == null ? "" : path.trim();
        return normalizedPath.equals("/api/social/posts")
                || normalizedPath.startsWith("/api/social/posts/")
                || normalizedPath.equals("/api/common/posts")
                || normalizedPath.startsWith("/api/common/posts/")
                || normalizedPath.equals("/api/posts")
                || normalizedPath.startsWith("/api/posts/");
    }

    @Bean
    @Order(-2)
    public GlobalFilter jwtAuthFilter(JwtTokenVerifier jwtTokenVerifier) {
        return (exchange, chain) -> {
            if (!securityEnabled) {
                return chain.filter(exchange);
            }

            // Always allow CORS preflight requests.
            if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
                return chain.filter(exchange);
            }

            String path = exchange.getRequest().getURI().getPath();
            String method = exchange.getRequest().getMethod() != null
                    ? exchange.getRequest().getMethod().name()
                    : "";
            log.info("🔒 Gateway filter: path={}", path);

            // For WebSocket paths: try to extract JWT from query param and inject
            // identity headers, but don't block if token is missing.
            if (isPublicGetPath(method, path)) {
                log.info("🔓 Public GET path detected, bypassing JWT check: {}", path);
                return chain.filter(exchange);
            }
            boolean isWsPath = path.startsWith("/api/common/ws") || path.startsWith("/api/social/ws");
            if (isWsPath) {
                log.info("🔌 WS path detected: {}", path);
                String queryToken = exchange.getRequest().getQueryParams().getFirst("token");
                if (queryToken != null && !queryToken.isBlank()) {
                    try {
                        var wsClaims = jwtTokenVerifier.parseAndValidate(queryToken);
                        log.info("✅ WS JWT valid: user={}", wsClaims.getSubject());
                        ServerHttpRequest wsRequest = exchange.getRequest().mutate()
                                .header("X-User-Id", wsClaims.get("userId", String.class))
                                .header("X-Username", wsClaims.getSubject())
                                .header("X-Role", wsClaims.get("role", String.class))
                                .build();
                        return chain.filter(exchange.mutate().request(wsRequest).build());
                    } catch (Exception e) {
                        log.warn("⚠️ WS JWT parse failed for path {}: {}", path, e.getMessage());
                    }
                } else {
                    log.warn("⚠️ WS path but no token query param: {}", path);
                }
                return chain.filter(exchange);
            }
            for (String prefix : PUBLIC_PATH_PREFIXES) {
                if (path.startsWith(prefix)) {
                    return chain.filter(exchange);
                }
            }

            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized(exchange, "Missing Bearer token");
            }

            String token = authHeader.substring("Bearer ".length()).trim();
            try {
                var claims = jwtTokenVerifier.parseAndValidate(token);
                String username = claims.getSubject();
                String role = claims.get("role", String.class);
                String userId = claims.get("userId", String.class);

                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                        .header("X-User-Id", userId != null ? userId : "")
                        .header("X-Username", username != null ? username : "")
                        .header("X-Role", role != null ? role : "")
                        .build();
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            } catch (Exception e) {
                return unauthorized(exchange, "Invalid token");
            }
        };
    }

    @Bean
    @Order(-1)
    public GlobalFilter requestLoggingFilter() {
        return (exchange, chain) -> {
            if (!log.isDebugEnabled()) {
                return chain.filter(exchange);
            }

            String path = exchange.getRequest().getURI().getPath();
            String method = exchange.getRequest().getMethod().toString();
            log.debug("Gateway Request: {} {}", method, path);
            
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                int statusCode = exchange.getResponse().getStatusCode() != null 
                    ? exchange.getResponse().getStatusCode().value() 
                    : 0;
                log.debug("Gateway Response: {} {} - Status: {}", method, path, statusCode);
            }));
        };
    }

    private Mono<Void> unauthorized(org.springframework.web.server.ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Ensure browser can read 401 responses (avoid generic "Failed to fetch" due to CORS).
        String origin = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (origin != null && !origin.isBlank()) {
            exchange.getResponse().getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            exchange.getResponse().getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        } else {
            exchange.getResponse().getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        }
        exchange.getResponse().getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        exchange.getResponse().getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*");

        byte[] body = ("{\"error\":\"unauthorized\",\"message\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}

