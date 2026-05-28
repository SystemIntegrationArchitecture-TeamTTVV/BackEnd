package edu.iuh.fit.se.messegeservice.event;

import java.time.Instant;

/**
 * Received from SocialService when a notification is created.
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
