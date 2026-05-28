package edu.iuh.fit.se.commonservice.saga;

import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.event.VideoCreatedEvent;
import edu.iuh.fit.se.commonservice.service.FriendService;
import edu.iuh.fit.se.commonservice.service.NotificationService;
import edu.iuh.fit.se.commonservice.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kafka consumer that handles video creation events asynchronously.
 * Notifies friends about new videos in the background.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoEventConsumer {

    private final FriendService friendService;
    private final NotificationService notificationService;
    private final UserIdentityService userIdentityService;

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

            for (String friendId : friendIds) {
                NotificationDTO dto = new NotificationDTO();
                dto.setType("VIDEO");
                dto.setActorId(event.authorId());
                dto.setActorName(author.getFullName());
                dto.setActorAvatar(author.getAvatar());
                dto.setRecipientId(friendId);
                dto.setRelatedId(event.videoId());
                dto.setRelatedType("VIDEO");
                dto.setTitle("Video mới");
                dto.setContent(author.getFullName() + " đã đăng video mới: " + event.title());
                dto.setRead(false);
                dto.setCreatedAt(LocalDateTime.now());
                notificationService.createNotification(dto);
            }
            log.info("[Kafka] Notified {} friends about video {}", friendIds.size(), event.videoId());
        } catch (Exception e) {
            log.error("[Kafka] Failed to process video created event: {}", e.getMessage(), e);
        }
    }
}
