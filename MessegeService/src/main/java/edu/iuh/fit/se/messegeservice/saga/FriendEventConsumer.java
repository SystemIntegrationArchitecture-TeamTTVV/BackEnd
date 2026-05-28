package edu.iuh.fit.se.messegeservice.saga;

import edu.iuh.fit.se.messegeservice.event.FriendAcceptedEvent;
import edu.iuh.fit.se.messegeservice.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that auto-creates a Direct conversation when two users become friends.
 * This ensures new friends can start chatting immediately without manual setup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FriendEventConsumer {

    private final ConversationService conversationService;

    @KafkaListener(topics = "ttvv.friend.accepted", groupId = "messegeservice-friend")
    public void onFriendAccepted(FriendAcceptedEvent event) {
        log.info("[Kafka] Friend accepted: userId1={}, userId2={}", event.userId1(), event.userId2());
        try {
            conversationService.getOrCreateDirectConversation(event.userId1(), event.userId2());
            log.info("[Kafka] Direct conversation created/ensured for friends {} ↔ {}",
                    event.userId1(), event.userId2());
        } catch (Exception e) {
            log.error("[Kafka] Failed to create conversation for friends {} ↔ {}: {}",
                    event.userId1(), event.userId2(), e.getMessage(), e);
        }
    }
}
