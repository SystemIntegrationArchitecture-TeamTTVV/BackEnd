package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
@Slf4j
@Controller
@RequiredArgsConstructor
// CORS is handled by API Gateway, no need for @CrossOrigin here
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle client connection and subscribe to user-specific channel
     */
    @MessageMapping("/connect")
    @SendTo("/topic/public")
    public SocketEventDTO handleConnect(SocketEventDTO event) {
        log.info("Client connected: {}", event);
        return event;
    }

    /**
     * Send notification to specific user
     */
    public void sendNotification(String userId, SocketEventDTO event) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", event);
    }

    /**
     * Send event to all users (for posts, reactions, etc.)
     */
    public void sendToAll(SocketEventDTO event) {
        messagingTemplate.convertAndSend("/topic/public", event);
    }

    /**
     * Send event to specific topic
     */
    public void sendToTopic(String topic, SocketEventDTO event) {
        messagingTemplate.convertAndSend("/topic/" + topic, event);
    }
}

