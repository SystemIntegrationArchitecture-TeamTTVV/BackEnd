package edu.iuh.fit.se.commonservice.dto;

import edu.iuh.fit.se.commonservice.config.socket.SocketEventTypes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SocketEventDTO {
    private String eventId;
    private String type; // NOTIFICATION, POST_CREATED, POST_UPDATED, COMMENT_CREATED, REACTION_ADDED, MESSAGE_RECEIVED
    private String userId; // Target user ID
    private Object data; // Event data
    private LocalDateTime timestamp;
    
    // Notification specific
    public static SocketEventDTO notification(String userId, NotificationDTO notification) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.NOTIFICATION);
        event.setUserId(userId);
        event.setData(notification);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
    
    // Post specific
    public static SocketEventDTO postCreated(String userId, PostDTO post) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.POST_CREATED);
        event.setUserId(userId);
        event.setData(post);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
    
    public static SocketEventDTO postUpdated(String userId, PostDTO post) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.POST_UPDATED);
        event.setUserId(userId);
        event.setData(post);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
    
    // Comment specific
    public static SocketEventDTO commentCreated(String userId, CommentDTO comment) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.COMMENT_CREATED);
        event.setUserId(userId);
        event.setData(comment);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
    
    // Reaction specific
    public static SocketEventDTO reactionAdded(String userId, ReactionDTO reaction) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.REACTION_ADDED);
        event.setUserId(userId);
        event.setData(reaction);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
    
    // Message specific
    public static SocketEventDTO messageReceived(String userId, Object messageData) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.MESSAGE_RECEIVED);
        event.setUserId(userId);
        event.setData(messageData);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
    
    // WebRTC Call specific
    public static SocketEventDTO callOffer(String userId, Object callData) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.CALL_OFFER);
        event.setUserId(userId);
        event.setData(callData);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
    
    public static SocketEventDTO callAnswer(String userId, Object answerData) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.CALL_ANSWER);
        event.setUserId(userId);
        event.setData(answerData);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
    
    public static SocketEventDTO callIceCandidate(String userId, Object candidateData) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.CALL_ICE_CANDIDATE);
        event.setUserId(userId);
        event.setData(candidateData);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
    
    public static SocketEventDTO callReject(String userId, Object rejectData) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.CALL_REJECT);
        event.setUserId(userId);
        event.setData(rejectData);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
    
    public static SocketEventDTO callEnd(String userId, Object endData) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(SocketEventTypes.CALL_END);
        event.setUserId(userId);
        event.setData(endData);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }

    public static SocketEventDTO of(String type, String userId, Object data) {
        SocketEventDTO event = new SocketEventDTO();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(type);
        event.setUserId(userId);
        event.setData(data);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }
}

