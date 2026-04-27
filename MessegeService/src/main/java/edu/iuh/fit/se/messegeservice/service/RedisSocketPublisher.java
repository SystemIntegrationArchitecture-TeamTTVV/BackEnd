package edu.iuh.fit.se.messegeservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes socket events via Redis Pub/Sub instead of synchronous Feign HTTP calls.
 * SocialService subscribes to these channels and emits via WebSocket to clients.
 *
 * Channels:
 *   - socket:emit:user    → emit to specific user (by username)
 *   - socket:emit:room    → emit to conversation room
 *   - socket:emit:topic   → emit to topic
 *   - socket:emit:all     → broadcast to all users
 */
@Slf4j
@Service
public class RedisSocketPublisher {

    private static final String CHANNEL_USER  = "socket:emit:user";
    private static final String CHANNEL_ROOM  = "socket:emit:room";
    private static final String CHANNEL_TOPIC = "socket:emit:topic";
    private static final String CHANNEL_ALL   = "socket:emit:all";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSocketPublisher(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Publish socket event to a specific user (by username).
     * Fire-and-forget — does not block the caller.
     */
    public void publishToUser(String username, SocketEventDTO event) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("target", username);
            message.put("event", event);
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(CHANNEL_USER, json);
            log.debug("⚡ Redis published to user {}: type={}", username, event.getType());
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize socket event for user {}: {}", username, e.getMessage());
        } catch (Exception e) {
            log.error("❌ Failed to publish socket event to user {}: {}", username, e.getMessage());
        }
    }

    /**
     * Publish socket event to a conversation room.
     */
    public void publishToRoom(String roomId, SocketEventDTO event) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("target", roomId);
            message.put("event", event);
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(CHANNEL_ROOM, json);
            log.debug("⚡ Redis published to room {}: type={}", roomId, event.getType());
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize socket event for room {}: {}", roomId, e.getMessage());
        } catch (Exception e) {
            log.error("❌ Failed to publish socket event to room {}: {}", roomId, e.getMessage());
        }
    }

    /**
     * Publish socket event to a topic.
     */
    public void publishToTopic(String topic, SocketEventDTO event) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("target", topic);
            message.put("event", event);
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(CHANNEL_TOPIC, json);
            log.debug("⚡ Redis published to topic {}: type={}", topic, event.getType());
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize socket event for topic {}: {}", topic, e.getMessage());
        } catch (Exception e) {
            log.error("❌ Failed to publish socket event to topic {}: {}", topic, e.getMessage());
        }
    }

    /**
     * Publish socket event to all connected users (broadcast).
     */
    public void publishToAll(SocketEventDTO event) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("event", event);
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(CHANNEL_ALL, json);
            log.debug("⚡ Redis published to all: type={}", event.getType());
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize broadcast socket event: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Failed to publish broadcast socket event: {}", e.getMessage());
        }
    }
}
