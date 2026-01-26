package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    /**
     * Send notification to specific user
     * @param username The username (principal name) of the user, not userId
     * @param event The socket event to send
     */
    public void sendNotification(String username, SocketEventDTO event) {
        log.info("📤 Sending notification to username {}: type={}, data={}", username, event.getType(), event.getData());
        // convertAndSendToUser uses the user's principal name (username), not userId
        // This will send to /user/{username}/queue/notifications
        messagingTemplate.convertAndSendToUser(username, "/queue/notifications", event);
        log.info("✅ Notification sent to username {} via /user/{}/queue/notifications", username, username);
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
        // Notify post author using username
        if (postAuthorId != null && !postAuthorId.equals(event.getUserId())) {
            userRepository.findById(postAuthorId).ifPresent(recipient -> {
                sendNotification(recipient.getUsername(), event);
            });
        }
        // Also broadcast to all for real-time updates
        sendToAll(event);
    }

    /**
     * Send reaction-related events
     */
    public void notifyReactionAdded(String postAuthorId, SocketEventDTO event) {
        // Notify post author using username if different user
        if (postAuthorId != null && !postAuthorId.equals(event.getUserId())) {
            userRepository.findById(postAuthorId).ifPresent(recipient -> {
                sendNotification(recipient.getUsername(), event);
            });
        }
        // Also broadcast to all for real-time updates
        sendToAll(event);
    }

    /**
     * Send message-related events
     */
    public void notifyMessageReceived(String recipientId, SocketEventDTO event) {
        userRepository.findById(recipientId).ifPresent(recipient -> {
            sendNotification(recipient.getUsername(), event);
        });
    }
}

