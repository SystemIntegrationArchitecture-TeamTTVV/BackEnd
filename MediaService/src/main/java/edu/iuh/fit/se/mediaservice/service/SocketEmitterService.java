package edu.iuh.fit.se.mediaservice.service;

import edu.iuh.fit.se.mediaservice.dto.SocketEventDTO;
import edu.iuh.fit.se.mediaservice.dto.UserDTO;
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

    public void emitToUser(String username, SocketEventDTO event) {
        try {
            redisSocketPublisher.publishToUser(username, event);
        } catch (Exception e) {
            log.error("Failed to publish socket event to user {}: {}", username, e.getMessage());
        }
    }

    public void emitToAll(SocketEventDTO event) {
        try {
            redisSocketPublisher.publishToAll(event);
        } catch (Exception e) {
            log.error("Failed to publish socket event to all: {}", e.getMessage());
        }
    }

    public void emitToTopic(String topic, SocketEventDTO event) {
        try {
            redisSocketPublisher.publishToTopic(topic, event);
        } catch (Exception e) {
            log.error("Failed to publish socket event to topic {}: {}", topic, e.getMessage());
        }
    }

    public void emitToRoom(String roomId, SocketEventDTO event) {
        try {
            redisSocketPublisher.publishToRoom(roomId, event);
        } catch (Exception e) {
            log.error("Failed to publish socket event to room {}: {}", roomId, e.getMessage());
        }
    }
}
