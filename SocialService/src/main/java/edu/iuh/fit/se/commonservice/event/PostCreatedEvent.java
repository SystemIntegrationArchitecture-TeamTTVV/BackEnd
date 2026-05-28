package edu.iuh.fit.se.commonservice.event;

import java.time.Instant;

/**
 * Published when a new post is saved.
 * Consumed by background workers to notify friends asynchronously.
 */
public record PostCreatedEvent(
        String postId,
        String authorId,
        String authorName,
        String visibility,
        String contentPreview,
        Instant occurredAt
) {
}
