package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.client.AuthServiceClient;
import edu.iuh.fit.se.messegeservice.client.CommonServiceClient;
import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.dto.UserDTO;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Wrapper around {@link CommonServiceClient} that adds Resilience4j circuit breaker
 * and centralises fallbacks for when CommonService is down.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommonServiceClientFacade {

    private final CommonServiceClient commonServiceClient;
    private final AuthServiceClient authServiceClient;

    @Value("${app.features.privacy-block-check.enabled:false}")
    private boolean privacyBlockCheckEnabled;

    @Cacheable(value = "msg-user-cache", key = "#id", unless = "#result == null || #result.username == 'unknown'")
    @Bulkhead(name = "authService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "getUserByIdFallback")
    @RateLimiter(name = "authService", fallbackMethod = "getUserByIdFallback")
    @Retry(name = "authService", fallbackMethod = "getUserByIdFallback")
    @CircuitBreaker(name = "authService", fallbackMethod = "getUserByIdFallback")
    public UserDTO getUserById(String id) {
        return authServiceClient.getUserById(id);
    }

    @Bulkhead(name = "authService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "batchLookupFallback")
    @RateLimiter(name = "authService", fallbackMethod = "batchLookupFallback")
    @Retry(name = "authService", fallbackMethod = "batchLookupFallback")
    @CircuitBreaker(name = "authService", fallbackMethod = "batchLookupFallback")
    public java.util.List<UserDTO> batchLookup(java.util.List<String> ids) {
        return authServiceClient.batchLookup(ids);
    }

    @SuppressWarnings("unused")
    private java.util.List<UserDTO> batchLookupFallback(java.util.List<String> ids, Throwable throwable) {
        log.warn("⚠️ [AuthServiceClient] Falling back for batchLookup: {}", throwable.getMessage());
        return java.util.List.of();
    }

    @SuppressWarnings("unused")
    private UserDTO getUserByIdFallback(String id, Throwable throwable) {
        log.warn("⚠️ [AuthServiceClient] Falling back for getUserById({}): {}", id, throwable.getMessage());
        UserDTO fallback = new UserDTO();
        fallback.setId(id);
        fallback.setUsername("unknown");
        fallback.setFullName("Unknown User");
        fallback.setAvatar(null);
        return fallback;
    }

    @Bulkhead(name = "commonService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "emitToUserFallback")
    @RateLimiter(name = "commonService", fallbackMethod = "emitToUserFallback")
    @CircuitBreaker(name = "commonService", fallbackMethod = "emitToUserFallback")
    public void emitToUser(String username, SocketEventDTO event) {
        commonServiceClient.emitToUser(username, event);
    }

    @SuppressWarnings("unused")
    private void emitToUserFallback(String username, SocketEventDTO event, Throwable throwable) {
        log.warn("⚠️ [CommonServiceClient] Failed to emitToUser({}) due to {}. Event type: {}",
                username, throwable.getMessage(), event != null ? event.getType() : "null");
    }

    @Bulkhead(name = "commonService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "emitToAllFallback")
    @RateLimiter(name = "commonService", fallbackMethod = "emitToAllFallback")
    @CircuitBreaker(name = "commonService", fallbackMethod = "emitToAllFallback")
    public void emitToAll(SocketEventDTO event) {
        commonServiceClient.emitToAll(event);
    }

    @SuppressWarnings("unused")
    private void emitToAllFallback(SocketEventDTO event, Throwable throwable) {
        log.warn("⚠️ [CommonServiceClient] Failed to emitToAll due to {}. Event type: {}",
                throwable.getMessage(), event != null ? event.getType() : "null");
    }

    @Bulkhead(name = "commonService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "emitToTopicFallback")
    @RateLimiter(name = "commonService", fallbackMethod = "emitToTopicFallback")
    @CircuitBreaker(name = "commonService", fallbackMethod = "emitToTopicFallback")
    public void emitToTopic(String topic, SocketEventDTO event) {
        commonServiceClient.emitToTopic(topic, event);
    }

    @SuppressWarnings("unused")
    private void emitToTopicFallback(String topic, SocketEventDTO event, Throwable throwable) {
        log.warn("⚠️ [CommonServiceClient] Failed to emitToTopic({}) due to {}. Event type: {}",
                topic, throwable.getMessage(), event != null ? event.getType() : "null");
    }

    @Bulkhead(name = "commonService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "emitToRoomFallback")
    @RateLimiter(name = "commonService", fallbackMethod = "emitToRoomFallback")
    @CircuitBreaker(name = "commonService", fallbackMethod = "emitToRoomFallback")
    public void emitToRoom(String roomId, SocketEventDTO event) {
        commonServiceClient.emitToRoom(roomId, event);
    }

    @SuppressWarnings("unused")
    private void emitToRoomFallback(String roomId, SocketEventDTO event, Throwable throwable) {
        log.warn("⚠️ [CommonServiceClient] Failed to emitToRoom({}) due to {}. Event type: {}",
                roomId, throwable.getMessage(), event != null ? event.getType() : "null");
    }

    // ── Privacy Check Methods ───────────────────────────────────────────

    @Bulkhead(name = "commonService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "canMessageFallback")
    @RateLimiter(name = "commonService", fallbackMethod = "canMessageFallback")
    @Retry(name = "commonService", fallbackMethod = "canMessageFallback")
    @CircuitBreaker(name = "commonService", fallbackMethod = "canMessageFallback")
    public boolean canMessage(String senderId, String receiverId) {
        if (!privacyBlockCheckEnabled) {
            return true;
        }
        if (!checkBlockPolicy("MESSAGE", senderId, receiverId)) {
            return false;
        }
        java.util.Map<String, Object> result = commonServiceClient.canMessage(senderId, receiverId);
        return Boolean.TRUE.equals(result.get("allowed"));
    }

    @SuppressWarnings("unused")
    private boolean canMessageFallback(String senderId, String receiverId, Throwable throwable) {
        log.warn("⚠️ [Privacy] canMessage fallback for {}→{}: {}", senderId, receiverId, throwable.getMessage());
        return true; // Fallback = allow
    }

    @Bulkhead(name = "commonService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "canCallFallback")
    @RateLimiter(name = "commonService", fallbackMethod = "canCallFallback")
    @Retry(name = "commonService", fallbackMethod = "canCallFallback")
    @CircuitBreaker(name = "commonService", fallbackMethod = "canCallFallback")
    public boolean canCall(String callerId, String receiverId) {
        if (!privacyBlockCheckEnabled) {
            return true;
        }
        if (!checkBlockPolicy("CALL", callerId, receiverId)) {
            return false;
        }
        java.util.Map<String, Object> result = commonServiceClient.canCall(callerId, receiverId);
        return Boolean.TRUE.equals(result.get("allowed"));
    }

    @SuppressWarnings("unused")
    private boolean canCallFallback(String callerId, String receiverId, Throwable throwable) {
        log.warn("⚠️ [Privacy] canCall fallback for {}→{}: {}", callerId, receiverId, throwable.getMessage());
        return true;
    }

    @Bulkhead(name = "commonService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "canInviteGroupFallback")
    @RateLimiter(name = "commonService", fallbackMethod = "canInviteGroupFallback")
    @Retry(name = "commonService", fallbackMethod = "canInviteGroupFallback")
    @CircuitBreaker(name = "commonService", fallbackMethod = "canInviteGroupFallback")
    public boolean canInviteGroup(String inviterId, String targetUserId) {
        if (!privacyBlockCheckEnabled) {
            return true;
        }
        if (!checkBlockPolicy("INVITE_GROUP", inviterId, targetUserId)) {
            return false;
        }
        java.util.Map<String, Object> result = commonServiceClient.canInviteGroup(inviterId, targetUserId);
        return Boolean.TRUE.equals(result.get("allowed"));
    }

    @SuppressWarnings("unused")
    private boolean canInviteGroupFallback(String inviterId, String targetUserId, Throwable throwable) {
        log.warn("⚠️ [Privacy] canInviteGroup fallback for {}→{}: {}", inviterId, targetUserId, throwable.getMessage());
        return true;
    }

    private boolean checkBlockPolicy(String action, String actorId, String targetId) {
        if (!privacyBlockCheckEnabled || actorId == null || targetId == null || actorId.equals(targetId)) {
            return true;
        }

        try {
            java.util.Map<String, Object> result;
            switch (action) {
                case "CALL" -> result = authServiceClient.canCallByBlock(actorId, targetId);
                case "INVITE_GROUP" -> result = authServiceClient.canInviteGroupByBlock(actorId, targetId);
                default -> result = authServiceClient.canMessageByBlock(actorId, targetId);
            }

            boolean allowed = Boolean.TRUE.equals(result.get("allowed"));
            if (!allowed) {
                Object reason = result.get("reason");
                log.info("🚫 [Privacy-Block] {} blocked for {}→{}: {}", action, actorId, targetId, reason);
            }
            return allowed;
        } catch (Exception e) {
            // Keep legacy behavior if block pre-check service is unavailable.
            log.warn("⚠️ [Privacy-Block] pre-check fallback for {} {}→{}: {}", action, actorId, targetId, e.getMessage());
            return true;
        }
    }
}

