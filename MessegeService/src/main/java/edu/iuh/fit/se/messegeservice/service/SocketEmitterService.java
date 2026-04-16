package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to emit socket events via CommonService
 * This service calls CommonService REST API to trigger socket events
 */
@Slf4j
@Service
public class SocketEmitterService {

    private final CommonServiceClientFacade commonServiceClientFacade;

    public SocketEmitterService(CommonServiceClientFacade commonServiceClientFacade) {
        this.commonServiceClientFacade = commonServiceClientFacade;
    }

    /**
     * Get username from userId by calling CommonService
     * @param userId The user ID
     * @return The username, or userId as fallback
     */
    private String getUsernameFromUserId(String userId) {
        try {
            UserDTO user = commonServiceClientFacade.getUserById(userId);
            return user != null && user.getUsername() != null ? user.getUsername() : userId;
        } catch (Exception e) {
            log.warn("⚠️ Failed to fetch username for userId {}, using userId as fallback: {}", userId, e.getMessage());
            // Fallback: use userId as username (for development, or if CommonService is down)
            return userId;
        }
    }

    /**
     * Emit socket event to specific user by userId
     * This method resolves userId → username before emitting
     * @param userId The recipient user ID
     * @param event The socket event
     */
    public void emitToUserById(String userId, SocketEventDTO event) {
        String username = getUsernameFromUserId(userId);
        emitToUser(username, event);
    }

    /**
     * Emit socket event to specific user by username
     * @param username The recipient username (not userId)
     * @param event The socket event
     */
    public void emitToUser(String username, SocketEventDTO event) {
        try {
            log.info("🚀 Emitting {} event to user {}", event.getType(), username);
            commonServiceClientFacade.emitToUser(username, event);
            log.info("✅ Socket event emitted successfully to user {}", username);
        } catch (Exception e) {
            log.error("❌ Failed to emit socket event to user {}: {}", username, e.getMessage());
            // Don't throw - socket emission is not critical, message is already saved
        }
    }

    /**
     * Emit socket event to all users
     * @param event The socket event
     */
    public void emitToAll(SocketEventDTO event) {
        try {
            log.info("🚀 Emitting {} event to all users", event.getType());
            commonServiceClientFacade.emitToAll(event);
            log.info("✅ Socket event emitted successfully to all users");
        } catch (Exception e) {
            log.error("❌ Failed to emit socket event to all users: {}", e.getMessage());
        }
    }

    /**
     * Emit socket event to specific topic
     * @param topic The topic name
     * @param event The socket event
     */
    public void emitToTopic(String topic, SocketEventDTO event) {
        try {
            log.info("🚀 Emitting {} event to topic {}", event.getType(), topic);
            commonServiceClientFacade.emitToTopic(topic, event);
            log.info("✅ Socket event emitted successfully to topic {}", topic);
        } catch (Exception e) {
            log.error("❌ Failed to emit socket event to topic {}: {}", topic, e.getMessage());
        }
    }

    /**
     * Emit socket event to a conversation room channel.
     */
    public void emitToRoom(String roomId, SocketEventDTO event) {
        try {
            log.info("🚀 Emitting {} event to room {}", event.getType(), roomId);
            commonServiceClientFacade.emitToRoom(roomId, event);
            log.info("✅ Socket event emitted successfully to room {}", roomId);
        } catch (Exception e) {
            log.error("❌ Failed to emit socket event to room {}: {}", roomId, e.getMessage());
        }
    }
}
