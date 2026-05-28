package edu.iuh.fit.se.commonservice.event;

import java.time.Instant;

/**
 * Published when a story is created.
 */
public record StoryCreatedEvent(
        String storyId,
        String authorId,
        Instant occurredAt
) {
}
