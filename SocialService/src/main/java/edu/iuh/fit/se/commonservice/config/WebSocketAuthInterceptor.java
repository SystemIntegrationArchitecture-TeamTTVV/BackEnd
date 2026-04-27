package edu.iuh.fit.se.commonservice.config;

import edu.iuh.fit.se.commonservice.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Extracts user identity during WebSocket handshake and stores it in session attributes.
 * Identity sources (in priority order):
 * 1. X-Username header (injected by API Gateway after JWT validation)
 * 2. ?token= query parameter (SockJS info requests that bypass gateway JWT filter)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final String USER_USERNAME_MAP_KEY = "user:username:map";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 1. Try Gateway-forwarded header
        String username = request.getHeaders().getFirst("X-Username");
        String userId = request.getHeaders().getFirst("X-UserId");
        if (username != null && !username.isBlank()) {
            attributes.put("username", username);
            if (userId != null && !userId.isBlank()) {
                attributes.put("userId", userId);
                cacheUsernameMapping(userId, username);
            }
            log.debug("WS handshake: username={}, userId={} (from gateway header)", username, userId);
            return true;
        }

        // 2. Fallback: extract from ?token= query param
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    String token = param.substring(6);
                    try {
                        username = jwtUtil.extractUsername(token);
                        if (username != null && jwtUtil.validateToken(token, username)) {
                            attributes.put("username", username);
                            // Extract userId from JWT and cache the mapping
                            try {
                                String tokenUserId = jwtUtil.extractClaim(token, claims -> claims.get("userId", String.class));
                                if (tokenUserId != null && !tokenUserId.isBlank()) {
                                    attributes.put("userId", tokenUserId);
                                    cacheUsernameMapping(tokenUserId, username);
                                }
                            } catch (Exception ignored) {
                                // Non-critical: mapping will be resolved via fallback
                            }
                            log.debug("WS handshake: username={} (from token param)", username);
                            return true;
                        }
                    } catch (Exception e) {
                        log.warn("WS handshake: invalid token: {}", e.getMessage());
                    }
                }
            }
        }

        log.warn("WS handshake: no identity found, allowing anonymous connection");
        return true; // allow connection, but user-specific features won't work
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    /**
     * Cache userId → username mapping in Redis Hash for cross-service lookup.
     * MessageService reads this to resolve usernames without Feign HTTP calls.
     */
    private void cacheUsernameMapping(String userId, String username) {
        try {
            stringRedisTemplate.opsForHash().put(USER_USERNAME_MAP_KEY, userId, username);
            log.debug("⚡ Cached userId→username mapping: {} → {}", userId, username);
        } catch (Exception e) {
            log.warn("Failed to cache username mapping for {}: {}", userId, e.getMessage());
        }
    }
}
