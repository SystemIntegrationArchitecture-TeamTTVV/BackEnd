package edu.iuh.fit.se.messegeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for socket events
 * This is a copy from CommonService to avoid inter-service dependencies
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SocketEventDTO {
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
        event.setType("MESSAGE_RECEIVED");
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
        event.setType("MESSAGE_SENT");
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
        event.setType("TYPING");
        event.setUserId(userId);
        event.setData(conversationId);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
}
