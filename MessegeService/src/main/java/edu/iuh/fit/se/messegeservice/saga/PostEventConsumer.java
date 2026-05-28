package edu.iuh.fit.se.messegeservice.saga;

import edu.iuh.fit.se.messegeservice.event.PostCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that receives post creation events from SocialService.
 * Can be extended to share posts into group chats or send system messages.
 */
@Slf4j
@Component
public class PostEventConsumer {

    @KafkaListener(topics = "ttvv.post.created", groupId = "messegeservice-post")
    public void onPostCreated(PostCreatedEvent event) {
        log.info("[Kafka] Post created event received: postId={}, authorId={}, author={}",
                event.postId(), event.authorId(), event.authorName());
    }
}
