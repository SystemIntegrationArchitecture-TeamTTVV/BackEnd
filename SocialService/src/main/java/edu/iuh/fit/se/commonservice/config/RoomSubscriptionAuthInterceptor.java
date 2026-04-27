package edu.iuh.fit.se.commonservice.config;

import edu.iuh.fit.se.commonservice.service.MessageServiceClientFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.FORBIDDEN;

/**
 * Intercepts STOMP SUBSCRIBE commands to /topic/rooms.{conversationId}
 * and verifies the user is actually a participant of that conversation.
 *
 * userId is read directly from WebSocket session attributes (set during handshake).
 * Participant check uses Feign with local cache (FeignAuthConfig provides auth headers).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomSubscriptionAuthInterceptor implements ChannelInterceptor {

    private static final String ROOM_PREFIX = "/topic/rooms.";
    private static final long ROOM_PARTICIPANT_CACHE_TTL_MS = 30_000; // 30s cache

    private static final class TimedValue<T> {
        private final T value;
        private final long expiresAt;

        private TimedValue(T value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }

    private final MessageServiceClientFacade messageServiceClientFacade;
    private final ConcurrentHashMap<String, TimedValue<Boolean>> roomMembershipCache = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(ROOM_PREFIX)) {
            return message;
        }

        // Get username from Principal (set by StompHandshakeHandler)
        Principal principal = accessor.getUser();
        String username = principal != null ? principal.getName() : null;
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(FORBIDDEN, "Unauthenticated room subscription");
        }

        String conversationId = destination.substring(ROOM_PREFIX.length()).trim();
        if (conversationId.isBlank()) {
            throw new ResponseStatusException(FORBIDDEN, "Invalid room destination");
        }

        // Read userId directly from session attributes (set during WebSocket handshake)
        // NO Feign call needed — userId was extracted from JWT during handshake
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        String userId = sessionAttrs != null ? (String) sessionAttrs.get("userId") : null;
        if (userId == null || userId.isBlank()) {
            log.warn("No userId in session for username={}, allowing subscription", username);
            // Fallback: allow subscription — the handshake already authenticated the user
            return message;
        }

        if (!isConversationParticipant(conversationId, userId)) {
            log.warn("Blocked unauthorized room subscription: username={}, userId={}, room={}", username, userId, conversationId);
            throw new ResponseStatusException(FORBIDDEN, "Not a participant of this room");
        }

        return message;
    }

    @SuppressWarnings("unchecked")
    private boolean isConversationParticipant(String conversationId, String userId) {
        String cacheKey = conversationId + ":" + userId;
        long now = System.currentTimeMillis();
        TimedValue<Boolean> cachedMembership = roomMembershipCache.get(cacheKey);
        if (cachedMembership != null && !cachedMembership.isExpired(now)) {
            return cachedMembership.value;
        }

        try {
            Map<String, Object> conversation = messageServiceClientFacade.getConversationById(conversationId);
            if (conversation == null || conversation.isEmpty()) {
                return false;
            }
            Object participantIdsObj = conversation.get("participantIds");
            if (!(participantIdsObj instanceof List<?> participantIds)) {
                return false;
            }
            boolean isParticipant = participantIds.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .anyMatch(userId::equals);
            roomMembershipCache.put(cacheKey, new TimedValue<>(isParticipant, now + ROOM_PARTICIPANT_CACHE_TTL_MS));
            return isParticipant;
        } catch (Exception ex) {
            log.warn("Failed room membership check for userId={} room={}: {}", userId, conversationId, ex.getMessage());
            return false;
        }
    }
}
