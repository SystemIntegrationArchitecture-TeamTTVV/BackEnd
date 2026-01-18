package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service to emit socket events via CommonService
 * This service calls CommonService REST API to trigger socket events
 */
@Slf4j
@Service
public class SocketEmitterService {

    private final RestTemplate restTemplate;
    private final String commonServiceUrl;

    public SocketEmitterService(
            RestTemplate restTemplate,
            @Value("${common.service.url:http://localhost:8081}") String commonServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.commonServiceUrl = commonServiceUrl;
    }

    /**
     * Get username from userId by calling CommonService
     * @param userId The user ID
     * @return The username, or userId as fallback
     */
    private String getUsernameFromUserId(String userId) {
        try {
            String url = commonServiceUrl + "/api/users/" + userId;
            UserDTO user = restTemplate.getForObject(url, UserDTO.class);
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
            String url = commonServiceUrl + "/api/socket/emit/user/" + username;
            log.info("🚀 Emitting {} event to user {} via {}", event.getType(), username, url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<SocketEventDTO> request = new HttpEntity<>(event, headers);
            
            restTemplate.postForEntity(url, request, Void.class);
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
            String url = commonServiceUrl + "/api/socket/emit/all";
            log.info("🚀 Emitting {} event to all users via {}", event.getType(), url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<SocketEventDTO> request = new HttpEntity<>(event, headers);
            
            restTemplate.postForEntity(url, request, Void.class);
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
            String url = commonServiceUrl + "/api/socket/emit/topic/" + topic;
            log.info("🚀 Emitting {} event to topic {} via {}", event.getType(), topic, url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<SocketEventDTO> request = new HttpEntity<>(event, headers);
            
            restTemplate.postForEntity(url, request, Void.class);
            log.info("✅ Socket event emitted successfully to topic {}", topic);
        } catch (Exception e) {
            log.error("❌ Failed to emit socket event to topic {}: {}", topic, e.getMessage());
        }
    }
}
