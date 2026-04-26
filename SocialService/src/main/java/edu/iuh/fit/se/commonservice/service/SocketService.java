package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.config.socket.SocketDestinations;
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
    private final UserIdentityService userIdentityService;

    public void sendNotification(String username, SocketEventDTO event) {
        log.info("Sending notification to username {}: type={}", username, event.getType());
        messagingTemplate.convertAndSendToUser(username, SocketDestinations.QUEUE_NOTIFICATIONS, event);
    }

    public void sendToAll(SocketEventDTO event) {
        messagingTemplate.convertAndSend(SocketDestinations.TOPIC_PUBLIC, event);
    }

    public void sendToTopic(String topic, SocketEventDTO event) {
        messagingTemplate.convertAndSend(SocketDestinations.BROKER_TOPIC + "/" + topic, event);
    }

    public void sendToRoom(String roomId, SocketEventDTO event) {
        messagingTemplate.convertAndSend(SocketDestinations.roomDestination(roomId), event);
    }

    public void notifyPostCreated(String authorId, SocketEventDTO event) {
        sendToAll(event);
    }

    public void notifyPostUpdated(String authorId, SocketEventDTO event) {
        sendToAll(event);
    }

    public void notifyCommentCreated(String postAuthorId, SocketEventDTO event) {
        if (postAuthorId != null && !postAuthorId.equals(event.getUserId())) {
            userIdentityService.findById(postAuthorId).ifPresent(recipient ->
                    sendNotification(recipient.getUsername(), event));
        }
        sendToAll(event);
    }

    public void notifyReactionAdded(String postAuthorId, SocketEventDTO event) {
        if (postAuthorId != null && !postAuthorId.equals(event.getUserId())) {
            userIdentityService.findById(postAuthorId).ifPresent(recipient ->
                    sendNotification(recipient.getUsername(), event));
        }
        sendToAll(event);
    }

    public void notifyMessageReceived(String recipientId, SocketEventDTO event) {
        userIdentityService.findById(recipientId).ifPresent(recipient ->
                sendNotification(recipient.getUsername(), event));
    }
}
