package edu.iuh.fit.se.messegeservice.saga;

import edu.iuh.fit.se.messegeservice.event.NotificationCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that receives notification events from SocialService.
 * Enables cross-service awareness for analytics and system messaging.
 */
@Slf4j
@Component
public class NotificationEventConsumer {

    @KafkaListener(topics = "ttvv.notification.created", groupId = "messegeservice-notification")
    public void onNotificationCreated(NotificationCreatedEvent event) {
        log.info("[Kafka] Notification received: type={}, recipientId={}, actorId={}",
                event.type(), event.recipientId(), event.actorId());
    }
}
