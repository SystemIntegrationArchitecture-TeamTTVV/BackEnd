package edu.iuh.fit.se.mediaservice.dto;

import edu.iuh.fit.se.mediaservice.config.socket.SocketEventTypes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for socket events
 * This is a copy from CommonService to avoid inter-service dependencies
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SocketEventDTO {
    private String eventId;
    private String type; // EVENT TYPE: MESSAGE_RECEIVED, MESSAGE_SENT, etc.
    private String userId; // Target user ID
    private Object data; // Event payload (MessageDTO)
    private LocalDateTime timestamp;

    /**
     * Factory method for MESSAGE_RECEIVED event
     * @param userId The recipient user ID
     * @param messageData The message data (MessageDTO)
     * @return SocketEventDTO
     */
    public static SocketEventDTO messageReceived(String userId, Object messageData) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.MESSAGE_RECEIVED);
        event.setUserId(userId);
        event.setData(messageData);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }

    /**
     * Factory method for MESSAGE_SENT event
     * @param userId The sender user ID
     * @param messageData The message data (MessageDTO)
     * @return SocketEventDTO
     */
    public static SocketEventDTO messageSent(String userId, Object messageData) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.MESSAGE_SENT);
        event.setUserId(userId);
        event.setData(messageData);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }

    /**
     * Factory method for TYPING event
     * @param userId The user ID who is typing
     * @param conversationId The conversation ID
     * @return SocketEventDTO
     */
    public static SocketEventDTO typing(String userId, String conversationId) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.TYPING);
        event.setUserId(userId);
        event.setData(conversationId);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }

    /**
     * Payload is typically a map with {@code conversationId} and {@code messageId}.
     */
    public static SocketEventDTO messageDeleted(String recipientUserId, Object data) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.MESSAGE_DELETED);
        event.setUserId(recipientUserId);
        event.setData(data);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }

    public static SocketEventDTO of(String type, String recipientUserId, Object data) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(type);
        event.setUserId(recipientUserId);
        event.setData(data);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
}
