package edu.iuh.fit.se.messegeservice.saga;

import edu.iuh.fit.se.messegeservice.dto.ConversationDTO;
import edu.iuh.fit.se.messegeservice.event.UserRegisteredEvent;
import edu.iuh.fit.se.messegeservice.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class UserOnboardingConsumer {

    private final ConversationService conversationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.system-user-id:system}")
    private String systemUserId;

    @KafkaListener(topics = "ttvv.user.registered", groupId = "messegeservice-onboarding")
    public void onUserRegistered(UserRegisteredEvent event) {
        try {
            if (event == null || event.userId() == null || event.userId().isBlank()) {
                return;
            }

            // Create (or ensure) a direct conversation between "system" and the new user.
            ConversationDTO conversationDTO = new ConversationDTO();
            conversationDTO.setParticipantIds(List.of(systemUserId, event.userId()));
            conversationDTO.setGroup(false);
            conversationService.getOrCreateDirectConversation(systemUserId, event.userId());

            kafkaTemplate.send("ttvv.user.onboarding.completed", event.userId(), new UserOnboardingCompletedEvent(
                    event.userId(),
                    Instant.now()
            ));
        } catch (Exception e) {
            log.warn("⚠️ [Saga] User onboarding failed for {}: {}", event != null ? event.userId() : "null", e.getMessage());
            kafkaTemplate.send("ttvv.user.onboarding.failed", event != null ? event.userId() : null, new UserOnboardingFailedEvent(
                    event != null ? event.userId() : null,
                    e.getMessage(),
                    Instant.now()
            ));
        }
    }

    public record UserOnboardingCompletedEvent(String userId, Instant occurredAt) {}
    public record UserOnboardingFailedEvent(String userId, String reason, Instant occurredAt) {}
}

