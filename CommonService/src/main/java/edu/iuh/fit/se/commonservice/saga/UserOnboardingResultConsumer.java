package edu.iuh.fit.se.commonservice.saga;

import edu.iuh.fit.se.commonservice.service.UserIdentityService;
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

    private final UserIdentityService userIdentityService;

    @KafkaListener(topics = "ttvv.user.onboarding.failed", groupId = "commonservice-onboarding")
    public void onOnboardingFailed(UserOnboardingFailedEvent event) {
        if (event == null || event.userId() == null || event.userId().isBlank()) {
            return;
        }
        userIdentityService.deactivateUser(event.userId());
        log.warn("[Saga] Deactivated user {} in Auth due to onboarding failure: {}", event.userId(), event.reason());
    }

    public record UserOnboardingFailedEvent(String userId, String reason) {}
}
