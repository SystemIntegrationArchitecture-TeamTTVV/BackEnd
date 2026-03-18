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
            "/api/common/auth",
            "/api/common/v3/api-docs",
            "/api/message/v3/api-docs",
            "/api/common/ws"
    );

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

                exchange.getRequest().mutate()
                        .header("X-User-Id", userId != null ? userId : "")
                        .header("X-Username", username != null ? username : "")
                        .header("X-Role", role != null ? role : "");
                return chain.filter(exchange);
            } catch (Exception e) {
                return unauthorized(exchange, "Invalid token");
            }
        };
    }

    @Bean
    @Order(-1)
    public GlobalFilter requestLoggingFilter() {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();
            String method = exchange.getRequest().getMethod().toString();
            log.info("🌐 Gateway Request: {} {}", method, path);
            
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                int statusCode = exchange.getResponse().getStatusCode() != null 
                    ? exchange.getResponse().getStatusCode().value() 
                    : 0;
                log.info("✅ Gateway Response: {} {} - Status: {}", method, path, statusCode);
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

