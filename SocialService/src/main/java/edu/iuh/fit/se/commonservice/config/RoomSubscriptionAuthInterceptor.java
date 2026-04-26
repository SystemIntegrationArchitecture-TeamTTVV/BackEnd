package edu.iuh.fit.se.commonservice.config;

import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
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
import java.util.Optional;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomSubscriptionAuthInterceptor implements ChannelInterceptor {

    private static final String ROOM_PREFIX = "/topic/rooms.";

    private final AuthServiceClient authServiceClient;
    private final MessageServiceClientFacade messageServiceClientFacade;

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

        Principal principal = accessor.getUser();
        String username = principal != null ? principal.getName() : null;
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(FORBIDDEN, "Unauthenticated room subscription");
        }

        String conversationId = destination.substring(ROOM_PREFIX.length()).trim();
        if (conversationId.isBlank()) {
            throw new ResponseStatusException(FORBIDDEN, "Invalid room destination");
        }

        UserDTO user = null;
        try {
            user = authServiceClient.getUserByUsername(username);
        } catch (Exception e) {}
        
        if (user == null) {
            throw new ResponseStatusException(FORBIDDEN, "User not found for room subscription");
        }

        String userId = user.getId();
        if (!isConversationParticipant(conversationId, userId)) {
            log.warn("Blocked unauthorized room subscription: username={}, userId={}, room={}", username, userId, conversationId);
            throw new ResponseStatusException(FORBIDDEN, "Not a participant of this room");
        }

        return message;
    }

    @SuppressWarnings("unchecked")
    private boolean isConversationParticipant(String conversationId, String userId) {
        try {
            Map<String, Object> conversation = messageServiceClientFacade.getConversationById(conversationId);
            if (conversation == null || conversation.isEmpty()) {
                return false;
            }
            Object participantIdsObj = conversation.get("participantIds");
            if (!(participantIdsObj instanceof List<?> participantIds)) {
                return false;
            }
            return participantIds.stream().filter(String.class::isInstance).map(String.class::cast).anyMatch(userId::equals);
        } catch (Exception ex) {
            log.warn("Failed room membership check for userId={} room={}: {}", userId, conversationId, ex.getMessage());
            return false;
        }
    }
}
