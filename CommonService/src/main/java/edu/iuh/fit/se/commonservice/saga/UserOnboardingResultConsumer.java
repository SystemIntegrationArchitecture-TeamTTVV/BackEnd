package edu.iuh.fit.se.commonservice.saga;

import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class UserOnboardingResultConsumer {

    private final UserRepository userRepository;

    @KafkaListener(topics = "ttvv.user.onboarding.failed", groupId = "commonservice-onboarding")
    public void onOnboardingFailed(UserOnboardingFailedEvent event) {
        if (event == null || event.userId() == null || event.userId().isBlank()) {
            return;
        }
        userRepository.findById(event.userId()).ifPresent(user -> {
            user.setIsActive(false);
            userRepository.save(user);
            log.warn("⚠️ [Saga] Marked user {} inactive due to onboarding failure: {}", event.userId(), event.reason());
        });
    }

    public record UserOnboardingFailedEvent(String userId, String reason) {}
}

