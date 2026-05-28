package edu.iuh.fit.se.commonservice.event;

import java.time.Instant;

/**
 * Published when a notification is persisted in SocialService.
 * Consumed by MessegeService for cross-service awareness.
 */
public record NotificationCreatedEvent(
        String recipientId,
        String actorId,
        String type,
        String title,
        String content,
        String relatedId,
        Instant occurredAt
) {
}
