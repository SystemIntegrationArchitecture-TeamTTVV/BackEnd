package edu.iuh.fit.se.commonservice.saga;

import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.event.ContentModerationRequestEvent;
import edu.iuh.fit.se.commonservice.model.Post;
import edu.iuh.fit.se.commonservice.repository.PostRepository;
import edu.iuh.fit.se.commonservice.service.AIViolationCheckService;
import edu.iuh.fit.se.commonservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * Kafka consumer for background AI content moderation.
 * Posts are published instantly; this worker checks content asynchronously.
 * If a violation is detected, the post is hidden and the author is notified.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentModerationConsumer {

    private final AIViolationCheckService aiViolationCheckService;
    private final PostRepository postRepository;
    private final NotificationService notificationService;

    @KafkaListener(topics = "ttvv.content.moderation.request", groupId = "commonservice-moderation")
    public void onModerationRequest(ContentModerationRequestEvent event) {
        log.info("[Kafka] Moderation request: contentId={}, type={}", event.contentId(), event.contentType());
        try {
            aiViolationCheckService.checkOrThrow(event.text(), event.contentType());
            log.info("[Kafka] Content {} passed moderation", event.contentId());
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                log.warn("[Kafka] Content {} VIOLATED: {}", event.contentId(), e.getReason());
                hideViolatedContent(event);
            } else {
                log.error("[Kafka] Moderation service unavailable for content {}: {}",
                        event.contentId(), e.getReason());
            }
        } catch (Exception e) {
            log.error("[Kafka] Unexpected error during moderation of {}: {}",
                    event.contentId(), e.getMessage(), e);
        }
    }

    private void hideViolatedContent(ContentModerationRequestEvent event) {
        if ("POST".equals(event.contentType())) {
            postRepository.findById(event.contentId()).ifPresent(post -> {
                post.setHidden(true);
                post.setUpdatedAt(LocalDateTime.now());
                postRepository.save(post);
                log.info("[Kafka] Post {} hidden due to AI moderation violation", event.contentId());

                // Notify the author
                NotificationDTO dto = new NotificationDTO();
                dto.setRecipientId(event.authorId());
                dto.setActorId("system");
                dto.setType("SYSTEM");
                dto.setTitle("Bài viết bị ẩn");
                dto.setContent("Bài viết của bạn đã bị ẩn do vi phạm tiêu chuẩn cộng đồng.");
                dto.setRelatedId(event.contentId());
                dto.setRelatedType("POST");
                dto.setRead(false);
                dto.setCreatedAt(LocalDateTime.now());
                notificationService.createNotification(dto);
            });
        }
    }
}
