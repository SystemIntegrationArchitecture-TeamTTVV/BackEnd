package edu.iuh.fit.se.commonservice.saga;

import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.event.VideoCreatedEvent;
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
 * Kafka consumer that handles video creation events asynchronously.
 * Dispatches bulk notification event for friends in a non-blocking way.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoEventConsumer {

    private final FriendService friendService;
    private final UserIdentityService userIdentityService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "ttvv.video.created", groupId = "commonservice-video")
    public void onVideoCreated(VideoCreatedEvent event) {
        log.info("[Kafka] Video created: videoId={}, authorId={}", event.videoId(), event.authorId());
        try {
            UserDTO author = userIdentityService.findById(event.authorId()).orElse(null);
            if (author == null) return;

            List<String> friendIds = friendService.getFriendsByUserId(event.authorId())
                    .stream()
                    .map(f -> f.getFriendId())
                    .toList();

            if (friendIds.isEmpty()) return;

            // Dispatch as a single bulk event to Kafka - immediate non-blocking return!
            NotificationBulkDispatchEvent bulkEvent = new NotificationBulkDispatchEvent(
                    "VIDEO",
                    event.authorId(),
                    author.getFullName(),
                    author.getAvatar(),
                    friendIds,
                    event.videoId(),
                    "VIDEO",
                    "Video mới",
                    author.getFullName() + " đã đăng video mới: " + event.title()
            );

            kafkaTemplate.send("ttvv.notification.bulk", event.videoId(), bulkEvent);
            log.info("[Kafka] Queued bulk video notifications for {} friends of author {}", friendIds.size(), event.authorId());
        } catch (Exception e) {
            log.error("[Kafka] Failed to process video created event: {}", e.getMessage(), e);
        }
    }
}
