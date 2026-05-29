package edu.iuh.fit.se.commonservice.saga;

import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.event.PostCreatedEvent;
import edu.iuh.fit.se.commonservice.event.NotificationBulkDispatchEvent;
import edu.iuh.fit.se.commonservice.service.FriendService;
import edu.iuh.fit.se.commonservice.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka consumer that handles post creation events asynchronously.
 * Dispatches bulk notification event for friends in a non-blocking way.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostEventConsumer {

    private final FriendService friendService;
    private final UserIdentityService userIdentityService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "ttvv.post.created", groupId = "commonservice-post")
    public void onPostCreated(PostCreatedEvent event) {
        log.info("[Kafka] Post created: postId={}, authorId={}", event.postId(), event.authorId());
        try {
            if ("PRIVATE".equals(event.visibility())) {
                return;
            }

            UserDTO author = userIdentityService.findById(event.authorId()).orElse(null);
            if (author == null) return;

            List<String> friendIds = friendService.getFriendsByUserId(event.authorId())
                    .stream()
                    .map(f -> f.getFriendId())
                    .toList();

            if (friendIds.isEmpty()) return;

            // Dispatch as a single bulk event to Kafka - immediate non-blocking return!
            NotificationBulkDispatchEvent bulkEvent = new NotificationBulkDispatchEvent(
                    "POST",
                    event.authorId(),
                    author.getFullName(),
                    author.getAvatar(),
                    friendIds,
                    event.postId(),
                    "POST",
                    "Bài viết mới",
                    author.getFullName() + " đã đăng bài viết mới"
            );

            kafkaTemplate.send("ttvv.notification.bulk", event.postId(), bulkEvent);
            log.info("[Kafka] Queued bulk post notifications for {} friends of author {}", friendIds.size(), event.authorId());
        } catch (Exception e) {
            log.error("[Kafka] Failed to process post created event: {}", e.getMessage(), e);
        }
    }
}
