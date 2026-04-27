package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to emit socket events via Redis Pub/Sub.
 * Previously used Feign HTTP calls to CommonService (slow, synchronous).
 * Now publishes to Redis channels — SocialService subscribes and emits via WebSocket.
 */
@Slf4j
@Service
public class SocketEmitterService {
    private static final long USERNAME_CACHE_TTL_MS = 60_000;

    private static final class CacheEntry {
        private final String username;
        private final long expiresAt;

        private CacheEntry(String username, long expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }

    private final RedisSocketPublisher redisSocketPublisher;
    private final CommonServiceClientFacade commonServiceClientFacade;
    private final ConcurrentHashMap<String, CacheEntry> usernameCache = new ConcurrentHashMap<>();

    public SocketEmitterService(RedisSocketPublisher redisSocketPublisher,
                                CommonServiceClientFacade commonServiceClientFacade) {
        this.redisSocketPublisher = redisSocketPublisher;
        this.commonServiceClientFacade = commonServiceClientFacade;
    }

    /**
     * Get username from userId (cached locally for 60s)
     */
    private String getUsernameFromUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return userId;
        }

        long now = System.currentTimeMillis();
        CacheEntry cached = usernameCache.get(userId);
        if (cached != null && !cached.isExpired(now)) {
            return cached.username;
        }

        try {
            UserDTO user = commonServiceClientFacade.getUserById(userId);
            if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
                usernameCache.put(userId, new CacheEntry(user.getUsername(), now + USERNAME_CACHE_TTL_MS));
                return user.getUsername();
            }
            return userId;
        } catch (Exception e) {
            log.warn("⚠️ Failed to fetch username for userId {}, using userId as fallback: {}", userId, e.getMessage());
            return userId;
        }
    }

    /**
     * Batch pre-cache userId → username mappings to avoid sequential Feign calls.
     * Only fetches IDs not already in cache.
     */
    public void preCacheUsernames(java.util.List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        java.util.List<String> uncachedIds = userIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .filter(id -> {
                    CacheEntry cached = usernameCache.get(id);
                    return cached == null || cached.isExpired(now);
                })
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        if (uncachedIds.isEmpty()) {
            return;
        }

        try {
            java.util.List<UserDTO> users = commonServiceClientFacade.batchLookup(uncachedIds);
            long cacheNow = System.currentTimeMillis();
            if (users != null) {
                for (UserDTO user : users) {
                    if (user != null && user.getId() != null && user.getUsername() != null && !user.getUsername().isBlank()) {
                        usernameCache.put(user.getId(), new CacheEntry(user.getUsername(), cacheNow + USERNAME_CACHE_TTL_MS));
                    }
                }
            }
            log.debug("⚡ Pre-cached {} usernames from batch lookup", users != null ? users.size() : 0);
        } catch (Exception e) {
            log.warn("⚠️ Failed to batch pre-cache usernames: {}", e.getMessage());
        }
    }

    /**
     * Emit socket event to specific user by userId.
     * Resolves userId → username, then publishes via Redis Pub/Sub.
     */
    public void emitToUserById(String userId, SocketEventDTO event) {
        String username = getUsernameFromUserId(userId);
        emitToUser(username, event);
    }

    /**
     * Emit socket event to specific user by username via Redis Pub/Sub.
     * ⚡ Fire-and-forget — does NOT block the caller.
     */
    public void emitToUser(String username, SocketEventDTO event) {
        try {
            log.info("⚡ Publishing {} event to user {} via Redis", event.getType(), username);
            redisSocketPublisher.publishToUser(username, event);
        } catch (Exception e) {
            log.error("❌ Failed to publish socket event to user {}: {}", username, e.getMessage());
        }
    }

    /**
     * Emit socket event to all users via Redis Pub/Sub.
     */
    public void emitToAll(SocketEventDTO event) {
        try {
            log.info("⚡ Publishing {} event to all users via Redis", event.getType());
            redisSocketPublisher.publishToAll(event);
        } catch (Exception e) {
            log.error("❌ Failed to publish socket event to all users: {}", e.getMessage());
        }
    }

    /**
     * Emit socket event to specific topic via Redis Pub/Sub.
     */
    public void emitToTopic(String topic, SocketEventDTO event) {
        try {
            log.info("⚡ Publishing {} event to topic {} via Redis", event.getType(), topic);
            redisSocketPublisher.publishToTopic(topic, event);
        } catch (Exception e) {
            log.error("❌ Failed to publish socket event to topic {}: {}", topic, e.getMessage());
        }
    }

    /**
     * Emit socket event to a conversation room via Redis Pub/Sub.
     */
    public void emitToRoom(String roomId, SocketEventDTO event) {
        try {
            log.info("⚡ Publishing {} event to room {} via Redis", event.getType(), roomId);
            redisSocketPublisher.publishToRoom(roomId, event);
        } catch (Exception e) {
            log.error("❌ Failed to publish socket event to room {}: {}", roomId, e.getMessage());
        }
    }
}
