package edu.iuh.fit.se.mediaservice.service;

import edu.iuh.fit.se.mediaservice.dto.SocketEventDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to emit socket events via Redis Pub/Sub.
 * Resolves userId → username via Redis Hash (populated by SocialService on WebSocket connect).
 * Lightweight version for MediaService — no Feign fallback, Redis-only lookup.
 */
@Slf4j
@Service
public class SocketEmitterService {
    private static final long USERNAME_CACHE_TTL_MS = 5 * 60_000; // 5 min
    private static final String USER_USERNAME_MAP_KEY = "user:username:map";

    private static final class CacheEntry {
        private final String username;
        private final long expiresAt;
        private CacheEntry(String username, long expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }
        private boolean isExpired(long now) { return now >= expiresAt; }
    }

    private final RedisSocketPublisher redisSocketPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ConcurrentHashMap<String, CacheEntry> usernameCache = new ConcurrentHashMap<>();

    public SocketEmitterService(RedisSocketPublisher redisSocketPublisher,
                                RedisTemplate<String, Object> redisTemplate) {
        this.redisSocketPublisher = redisSocketPublisher;
        this.redisTemplate = redisTemplate;
    }

    private String getUsernameFromUserId(String userId) {
        if (userId == null || userId.isBlank()) return userId;

        long now = System.currentTimeMillis();

        // 1. Local cache
        CacheEntry cached = usernameCache.get(userId);
        if (cached != null && !cached.isExpired(now)) return cached.username;

        // 2. Redis Hash
        try {
            Object redisValue = redisTemplate.opsForHash().get(USER_USERNAME_MAP_KEY, userId);
            if (redisValue != null) {
                String username = redisValue.toString();
                if (!username.isBlank()) {
                    usernameCache.put(userId, new CacheEntry(username, now + USERNAME_CACHE_TTL_MS));
                    return username;
                }
            }
        } catch (Exception e) {
            log.debug("Redis Hash lookup failed for {}: {}", userId, e.getMessage());
        }

        // 3. Fallback: use userId as username
        return userId;
    }

    public void emitToUserById(String userId, SocketEventDTO event) {
        String username = getUsernameFromUserId(userId);
        emitToUser(username, event);
    }

    @CircuitBreaker(name = "redisSocket", fallbackMethod = "emitToUserFallback")
    public void emitToUser(String username, SocketEventDTO event) {
        redisSocketPublisher.publishToUser(username, event);
    }

    public void emitToUserFallback(String username, SocketEventDTO event, Throwable t) {
        log.error("🚨 [CB-FALLBACK] Redis socket publisher is down! Failed to emit to user {}: {}", username, t.getMessage());
    }

    @CircuitBreaker(name = "redisSocket", fallbackMethod = "emitToAllFallback")
    public void emitToAll(SocketEventDTO event) {
        redisSocketPublisher.publishToAll(event);
    }

    public void emitToAllFallback(SocketEventDTO event, Throwable t) {
        log.error("🚨 [CB-FALLBACK] Redis socket publisher is down! Failed to emit to all: {}", t.getMessage());
    }

    @CircuitBreaker(name = "redisSocket", fallbackMethod = "emitToTopicFallback")
    public void emitToTopic(String topic, SocketEventDTO event) {
        redisSocketPublisher.publishToTopic(topic, event);
    }

    public void emitToTopicFallback(String topic, SocketEventDTO event, Throwable t) {
        log.error("🚨 [CB-FALLBACK] Redis socket publisher is down! Failed to emit to topic {}: {}", topic, t.getMessage());
    }

    @CircuitBreaker(name = "redisSocket", fallbackMethod = "emitToRoomFallback")
    public void emitToRoom(String roomId, SocketEventDTO event) {
        redisSocketPublisher.publishToRoom(roomId, event);
    }

    public void emitToRoomFallback(String roomId, SocketEventDTO event, Throwable t) {
        log.error("🚨 [CB-FALLBACK] Redis socket publisher is down! Failed to emit to room {}: {}", roomId, t.getMessage());
    }
}
