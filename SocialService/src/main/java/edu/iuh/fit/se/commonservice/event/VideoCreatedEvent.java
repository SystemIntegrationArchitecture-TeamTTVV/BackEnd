package edu.iuh.fit.se.commonservice.event;

import java.time.Instant;

/**
 * Published when a new video is uploaded.
 * Consumed by background workers to notify friends asynchronously.
 */
public record VideoCreatedEvent(
        String videoId,
        String authorId,
        String authorName,
        String title,
        Instant occurredAt
) {
}
