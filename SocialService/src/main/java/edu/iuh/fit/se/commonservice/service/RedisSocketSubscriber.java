package edu.iuh.fit.se.commonservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.iuh.fit.se.commonservice.config.socket.SocketDestinations;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Subscribes to Redis Pub/Sub channels and emits socket events via WebSocket (STOMP).
 * Replaces the REST controller /api/socket/emit/* endpoints that MessegeService used to call via Feign.
 *
 * Channels:
 *   - socket:emit:user    → emit to specific user
 *   - socket:emit:room    → emit to conversation room
 *   - socket:emit:topic   → emit to topic
 *   - socket:emit:all     → broadcast to all users
 */
@Slf4j
@Service
public class RedisSocketSubscriber {

    private static final String CHANNEL_USER  = "socket:emit:user";
    private static final String CHANNEL_ROOM  = "socket:emit:room";
    private static final String CHANNEL_TOPIC = "socket:emit:topic";
    private static final String CHANNEL_ALL   = "socket:emit:all";

    private final RedisMessageListenerContainer listenerContainer;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisSocketSubscriber(RedisMessageListenerContainer listenerContainer,
                                 SimpMessagingTemplate messagingTemplate) {
        this.listenerContainer = listenerContainer;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void subscribe() {
        log.info("🔌 Subscribing to Redis socket channels...");

        listenerContainer.addMessageListener(userListener(), new ChannelTopic(CHANNEL_USER));
        listenerContainer.addMessageListener(roomListener(), new ChannelTopic(CHANNEL_ROOM));
        listenerContainer.addMessageListener(topicListener(), new ChannelTopic(CHANNEL_TOPIC));
        listenerContainer.addMessageListener(allListener(), new ChannelTopic(CHANNEL_ALL));

        log.info("✅ Subscribed to Redis channels: {}, {}, {}, {}",
                CHANNEL_USER, CHANNEL_ROOM, CHANNEL_TOPIC, CHANNEL_ALL);
    }

    private MessageListener userListener() {
        return (Message message, byte[] pattern) -> {
            try {
                String json = new String(message.getBody());
                JsonNode node = objectMapper.readTree(json);
                String username = node.get("target").asText();
                SocketEventDTO event = objectMapper.treeToValue(node.get("event"), SocketEventDTO.class);

                log.info("⚡ Redis → WebSocket: emit to user {}, type={}", username, event.getType());
                messagingTemplate.convertAndSendToUser(
                        username, SocketDestinations.QUEUE_NOTIFICATIONS, event);
            } catch (Exception e) {
                log.error("❌ Failed to process Redis user socket event: {}", e.getMessage());
            }
        };
    }

    private MessageListener roomListener() {
        return (Message message, byte[] pattern) -> {
            try {
                String json = new String(message.getBody());
                JsonNode node = objectMapper.readTree(json);
                String roomId = node.get("target").asText();
                SocketEventDTO event = objectMapper.treeToValue(node.get("event"), SocketEventDTO.class);

                log.info("⚡ Redis → WebSocket: emit to room {}, type={}", roomId, event.getType());
                messagingTemplate.convertAndSend(
                        SocketDestinations.roomDestination(roomId), event);
            } catch (Exception e) {
                log.error("❌ Failed to process Redis room socket event: {}", e.getMessage());
            }
        };
    }

    private MessageListener topicListener() {
        return (Message message, byte[] pattern) -> {
            try {
                String json = new String(message.getBody());
                JsonNode node = objectMapper.readTree(json);
                String topic = node.get("target").asText();
                SocketEventDTO event = objectMapper.treeToValue(node.get("event"), SocketEventDTO.class);

                log.debug("⚡ Redis → WebSocket: emit to topic {}, type={}", topic, event.getType());
                messagingTemplate.convertAndSend(
                        SocketDestinations.BROKER_TOPIC + "/" + topic, event);
            } catch (Exception e) {
                log.error("❌ Failed to process Redis topic socket event: {}", e.getMessage());
            }
        };
    }

    private MessageListener allListener() {
        return (Message message, byte[] pattern) -> {
            try {
                String json = new String(message.getBody());
                JsonNode node = objectMapper.readTree(json);
                SocketEventDTO event = objectMapper.treeToValue(node.get("event"), SocketEventDTO.class);

                log.debug("⚡ Redis → WebSocket: broadcast, type={}", event.getType());
                messagingTemplate.convertAndSend(SocketDestinations.TOPIC_PUBLIC, event);
            } catch (Exception e) {
                log.error("❌ Failed to process Redis broadcast socket event: {}", e.getMessage());
            }
        };
    }
}
