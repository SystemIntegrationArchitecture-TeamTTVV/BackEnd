package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocketService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Send notification to specific user
     */
    public void sendNotification(String userId, SocketEventDTO event) {
        log.debug("Sending notification to user {}: {}", userId, event.getType());
        messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", event);
    }

    /**
     * Send event to all users (for posts, reactions, etc.)
     */
    public void sendToAll(SocketEventDTO event) {
        log.debug("Sending event to all users: {}", event.getType());
        messagingTemplate.convertAndSend("/topic/public", event);
    }

    /**
     * Send event to specific topic
     */
    public void sendToTopic(String topic, SocketEventDTO event) {
        log.debug("Sending event to topic {}: {}", topic, event.getType());
        messagingTemplate.convertAndSend("/topic/" + topic, event);
    }

    /**
     * Send post-related events
     */
    public void notifyPostCreated(String authorId, SocketEventDTO event) {
        sendToAll(event);
    }

    public void notifyPostUpdated(String authorId, SocketEventDTO event) {
        sendToAll(event);
    }

    /**
     * Send comment-related events
     */
    public void notifyCommentCreated(String postAuthorId, SocketEventDTO event) {
        // Notify post author
        if (postAuthorId != null && !postAuthorId.equals(event.getUserId())) {
            sendNotification(postAuthorId, event);
        }
        // Also broadcast to all for real-time updates
        sendToAll(event);
    }

    /**
     * Send reaction-related events
     */
    public void notifyReactionAdded(String postAuthorId, SocketEventDTO event) {
        // Notify post author if different user
        if (postAuthorId != null && !postAuthorId.equals(event.getUserId())) {
            sendNotification(postAuthorId, event);
        }
        // Also broadcast to all for real-time updates
        sendToAll(event);
    }

    /**
     * Send message-related events
     */
    public void notifyMessageReceived(String recipientId, SocketEventDTO event) {
        sendNotification(recipientId, event);
    }
}

