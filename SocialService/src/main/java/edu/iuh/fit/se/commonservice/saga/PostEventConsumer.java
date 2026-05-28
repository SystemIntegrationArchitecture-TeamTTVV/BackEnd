package edu.iuh.fit.se.commonservice.saga;

import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.event.PostCreatedEvent;
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
 * Kafka consumer that handles post creation events asynchronously.
 * Notifies friends about new posts in the background — user gets instant response.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostEventConsumer {

    private final FriendService friendService;
    private final NotificationService notificationService;
    private final UserIdentityService userIdentityService;

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

            for (String friendId : friendIds) {
                NotificationDTO dto = new NotificationDTO();
                dto.setType("POST");
                dto.setActorId(event.authorId());
                dto.setActorName(author.getFullName());
                dto.setActorAvatar(author.getAvatar());
                dto.setRecipientId(friendId);
                dto.setRelatedId(event.postId());
                dto.setRelatedType("POST");
                dto.setTitle("Bài viết mới");
                dto.setContent(author.getFullName() + " đã đăng bài viết mới");
                dto.setRead(false);
                dto.setCreatedAt(LocalDateTime.now());
                notificationService.createNotification(dto);
            }
            log.info("[Kafka] Notified {} friends about post {}", friendIds.size(), event.postId());
        } catch (Exception e) {
            log.error("[Kafka] Failed to process post created event: {}", e.getMessage(), e);
        }
    }
}
