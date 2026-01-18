package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Optional;
@Slf4j
@Controller
@RequiredArgsConstructor
// CORS is handled by API Gateway, no need for @CrossOrigin here
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

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

    /**
     * WebRTC Signaling: Handle call offer
     */
    @MessageMapping("/webrtc/offer")
    public void handleCallOffer(SocketEventDTO event) {
        log.info("📞 Call offer received from client");
        log.info("📞 Event details: type={}, userId={}, data={}", event.getType(), event.getUserId(), event.getData());
        
        // Get username from userId for Spring WebSocket routing
        String recipientUsername = getUsernameById(event.getUserId());
        if (recipientUsername != null) {
            log.info("✅ Forwarding call offer to username: {} (userId: {})", recipientUsername, event.getUserId());
            messagingTemplate.convertAndSendToUser(recipientUsername, "/queue/webrtc", event);
            log.info("✅ Call offer forwarded successfully to {}", recipientUsername);
        } else {
            log.error("❌ User not found for ID: {}", event.getUserId());
        }
    }

    /**
     * WebRTC Signaling: Handle call answer
     */
    @MessageMapping("/webrtc/answer")
    public void handleCallAnswer(SocketEventDTO event) {
        log.info("Call answer received: {}", event);
        String recipientUsername = getUsernameById(event.getUserId());
        if (recipientUsername != null) {
            log.info("Forwarding call answer to username: {}", recipientUsername);
            messagingTemplate.convertAndSendToUser(recipientUsername, "/queue/webrtc", event);
        } else {
            log.error("User not found for ID: {}", event.getUserId());
        }
    }

    /**
     * WebRTC Signaling: Handle ICE candidate
     */
    @MessageMapping("/webrtc/ice-candidate")
    public void handleIceCandidate(SocketEventDTO event) {
        log.info("ICE candidate received: {}", event);
        String recipientUsername = getUsernameById(event.getUserId());
        if (recipientUsername != null) {
            messagingTemplate.convertAndSendToUser(recipientUsername, "/queue/webrtc", event);
        } else {
            log.error("User not found for ID: {}", event.getUserId());
        }
    }

    /**
     * WebRTC Signaling: Handle call rejection
     */
    @MessageMapping("/webrtc/reject")
    public void handleCallReject(SocketEventDTO event) {
        log.info("Call rejected: {}", event);
        String recipientUsername = getUsernameById(event.getUserId());
        if (recipientUsername != null) {
            messagingTemplate.convertAndSendToUser(recipientUsername, "/queue/webrtc", event);
        } else {
            log.error("User not found for ID: {}", event.getUserId());
        }
    }

    /**
     * WebRTC Signaling: Handle call end
     */
    @MessageMapping("/webrtc/end")
    public void handleCallEnd(SocketEventDTO event) {
        log.info("Call ended: {}", event);
        String recipientUsername = getUsernameById(event.getUserId());
        if (recipientUsername != null) {
            messagingTemplate.convertAndSendToUser(recipientUsername, "/queue/webrtc", event);
        } else {
            log.error("User not found for ID: {}", event.getUserId());
        }
    }

    /**
     * Helper method to get username from userId
     * Spring WebSocket's convertAndSendToUser() uses username (principal name), not userId
     */
    private String getUsernameById(String userId) {
        try {
            log.info("🔍 Looking up user with ID: {}", userId);
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isPresent()) {
                User user = userOptional.get();
                log.info("✅ Found user: id={}, username={}, fullName={}", user.getId(), user.getUsername(), user.getFullName());
                return user.getUsername();
            } else {
                log.error("❌ No user found in database with ID: {}", userId);
                // Try to list all users to debug
                long totalUsers = userRepository.count();
                log.info("📊 Total users in database: {}", totalUsers);
                return null;
            }
        } catch (Exception e) {
            log.error("❌ Error finding user by ID: {}", userId, e);
            return null;
        }
    }
}

