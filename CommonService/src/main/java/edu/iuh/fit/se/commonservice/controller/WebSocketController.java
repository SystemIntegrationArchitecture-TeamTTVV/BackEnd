package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import edu.iuh.fit.se.commonservice.service.MessageServiceClientFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
@Slf4j
@Controller
@RequiredArgsConstructor
// CORS is handled by API Gateway, no need for @CrossOrigin here
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final MessageServiceClientFacade messageServiceClientFacade;

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
     * Supports both direct calls (1-1) and group calls
     */
    @MessageMapping("/webrtc/offer")
    public void handleCallOffer(SocketEventDTO event) {
        log.info("📞 Call offer received from client");
        log.info("📞 Event details: type={}, userId={}, data={}", event.getType(), event.getUserId(), event.getData());
        
        try {
            // Check if this is a group call by examining the data
            Map<String, Object> callData = (Map<String, Object>) event.getData();
            Boolean isGroup = callData != null ? (Boolean) callData.get("isGroup") : null;
            String conversationId = callData != null ? (String) callData.get("conversationId") : null;
            
            // Group call: broadcast to all participants in the conversation
            if (Boolean.TRUE.equals(isGroup) && conversationId != null) {
                log.info("📞 Group call detected - conversationId: {}", conversationId);
                broadcastGroupCallOffer(event, conversationId);
            } else {
                // Direct call: forward to single recipient
                String recipientUsername = getUsernameById(event.getUserId());
                if (recipientUsername != null) {
                    log.info("✅ Forwarding call offer to username: {} (userId: {})", recipientUsername, event.getUserId());
                    messagingTemplate.convertAndSendToUser(recipientUsername, "/queue/webrtc", event);
                    log.info("✅ Call offer forwarded successfully to {}", recipientUsername);
                } else {
                    log.error("❌ User not found for ID: {}", event.getUserId());
                }
            }
        } catch (Exception e) {
            log.error("❌ Error handling call offer: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast call offer to all participants in a group conversation
     */
    private void broadcastGroupCallOffer(SocketEventDTO event, String conversationId) {
        try {
            log.info("📞 Broadcasting group call offer to conversation: {}", conversationId);

            Map<String, Object> conversation = messageServiceClientFacade.getConversationById(conversationId);
            
            if (conversation == null) {
                log.error("❌ Conversation not found: {}", conversationId);
                return;
            }
            
            // Get participants list
            @SuppressWarnings("unchecked")
            List<String> participantIds = (List<String>) conversation.get("participantIds");
            
            if (participantIds == null || participantIds.isEmpty()) {
                log.error("❌ Conversation has no participants: {}", conversationId);
                return;
            }
            
            // Get caller ID from event data
            Map<String, Object> callData = (Map<String, Object>) event.getData();
            String callerId = callData != null ? (String) callData.get("callerId") : null;
            
            // Broadcast to all participants except the caller
            int broadcastCount = 0;
            for (String participantId : participantIds) {
                // Skip the caller
                if (callerId != null && participantId.equals(callerId)) {
                    log.debug("⏭️ Skipping caller: {}", participantId);
                    continue;
                }
                
                try {
                    String participantUsername = getUsernameById(participantId);
                    if (participantUsername != null) {
                        // Create new event for this participant
                        SocketEventDTO participantEvent = new SocketEventDTO();
                        participantEvent.setType(event.getType());
                        participantEvent.setUserId(participantId);
                        participantEvent.setData(event.getData());
                        participantEvent.setTimestamp(event.getTimestamp());
                        
                        messagingTemplate.convertAndSendToUser(participantUsername, "/queue/webrtc", participantEvent);
                        broadcastCount++;
                        log.debug("✅ Broadcasted call offer to participant: {} (username: {})", participantId, participantUsername);
                    } else {
                        log.warn("⚠️ Participant username not found for ID: {}", participantId);
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to broadcast to participant {}: {}", participantId, e.getMessage());
                }
            }
            
            log.info("✅ Successfully broadcasted group call offer to {}/{} participants", 
                broadcastCount, participantIds.size() - (callerId != null ? 1 : 0));
        } catch (Exception e) {
            log.error("❌ Error broadcasting group call offer: {}", e.getMessage(), e);
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

