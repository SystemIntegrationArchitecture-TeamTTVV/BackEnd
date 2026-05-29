package edu.iuh.fit.se.commonservice.saga;

import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.event.NotificationBulkDispatchEvent;
import edu.iuh.fit.se.commonservice.model.Notification;
import edu.iuh.fit.se.commonservice.repository.NotificationRepository;
import edu.iuh.fit.se.commonservice.service.SocketService;
import edu.iuh.fit.se.commonservice.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationBulkConsumer {

    private static final String USER_USERNAME_MAP_KEY = "user:username:map";
    private static final int BATCH_SIZE = 1000;

    private final NotificationRepository notificationRepository;
    private final UserIdentityService userIdentityService;
    private final SocketService socketService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ThreadPoolTaskExecutor wsOutboundExecutor; // For non-blocking WS sends
    private final MongoTemplate mongoTemplate;

    @KafkaListener(topics = "ttvv.notification.bulk", groupId = "commonservice-bulk-notifications")
    public void onBulkNotification(NotificationBulkDispatchEvent event) {
        List<String> recipients = event.recipientIds();
        if (recipients == null || recipients.isEmpty()) return;

        log.info("[Kafka] Received bulk notification request: type={}, totalRecipients={}", event.type(), recipients.size());
        
        List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();
        // Partition recipients into batches of 1000 and process in parallel
        for (int i = 0; i < recipients.size(); i += BATCH_SIZE) {
            final List<String> batch = recipients.subList(i, Math.min(i + BATCH_SIZE, recipients.size()));
            futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> processBatch(batch, event), wsOutboundExecutor));
        }
        
        // Wait for all parallel batches to complete to ensure data consistency and offset commit
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        log.info("[Kafka] Finished processing bulk notifications for event: relatedId={}", event.relatedId());
    }

    private void processBatch(List<String> recipientIds, NotificationBulkDispatchEvent event) {
        try {
            long start = System.currentTimeMillis();
            
            // 1. Resolve Usernames using Redis Cache or Batch Feign lookup
            Map<String, String> userIdToUsernameMap = new HashMap<>();
            List<String> missingIds = new ArrayList<>();

            // Quick lookup from Redis mapping (cached during WebSocket handshakes)
            try {
                List<Object> cachedUsernames = stringRedisTemplate.opsForHash().multiGet(USER_USERNAME_MAP_KEY, new ArrayList<>(recipientIds));
                for (int i = 0; i < recipientIds.size(); i++) {
                    String rId = recipientIds.get(i);
                    String username = cachedUsernames != null && cachedUsernames.size() > i ? (String) cachedUsernames.get(i) : null;
                    if (username != null) {
                        userIdToUsernameMap.put(rId, username);
                    } else {
                        missingIds.add(rId);
                    }
                }
            } catch (Exception e) {
                log.warn("[Redis] Failed to multiGet cached usernames: {}", e.getMessage());
                missingIds.addAll(recipientIds);
            }

            // Batch REST lookup for missing IDs in a single query
            if (!missingIds.isEmpty()) {
                Map<String, UserDTO> resolvedUsers = userIdentityService.batchLookupMap(missingIds);
                resolvedUsers.forEach((id, dto) -> {
                    if (dto != null && dto.getUsername() != null) {
                        userIdToUsernameMap.put(id, dto.getUsername());
                    }
                });
            }

            // 2. Map and Save all Notification entities in ONE single MongoDB database command
            List<Notification> entities = recipientIds.stream().map(recipientId -> {
                Notification notification = new Notification();
                notification.setRecipientId(recipientId);
                notification.setActorId(event.actorId());
                notification.setType(event.type());
                notification.setTitle(event.title());
                notification.setContent(event.content());
                notification.setRelatedId(event.relatedId());
                notification.setRelatedType(event.relatedType());
                notification.setRead(false);
                notification.setCreatedAt(LocalDateTime.now());
                return notification;
            }).collect(Collectors.toList());

            // Bulk Save using MongoTemplate.insert to execute a single high-performance bulk insert query
            Collection<Notification> savedEntities = mongoTemplate.insert(entities, Notification.class);
            long dbTime = System.currentTimeMillis() - start;

            // 3. Deliver WebSockets concurrently (runs on the parallel worker thread)
            for (Notification saved : savedEntities) {
                String username = userIdToUsernameMap.get(saved.getRecipientId());
                if (username != null) {
                    // User is registered/online, send Socket notification
                    NotificationDTO dto = toDTO(saved, username, event.actorName(), event.actorAvatar());
                    socketService.sendNotification(
                            username,
                            SocketEventDTO.notification(saved.getRecipientId(), dto)
                    );
                }
            }

            log.info("⚡ Processed batch of {} notifications: DB write took {} ms", recipientIds.size(), dbTime);
        } catch (Exception e) {
            log.error("Failed to process notification batch: {}", e.getMessage(), e);
        }
    }

    private NotificationDTO toDTO(Notification saved, String recipientName, String actorName, String actorAvatar) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(saved.getId());
        dto.setRecipientId(saved.getRecipientId());
        dto.setRecipientName(recipientName);
        dto.setActorId(saved.getActorId());
        dto.setActorName(actorName);
        dto.setActorAvatar(actorAvatar);
        dto.setType(saved.getType());
        dto.setTitle(saved.getTitle());
        dto.setContent(saved.getContent());
        dto.setRelatedId(saved.getRelatedId());
        dto.setRelatedType(saved.getRelatedType());
        dto.setRead(saved.isRead());
        dto.setCreatedAt(saved.getCreatedAt());
        return dto;
    }
}
