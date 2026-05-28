package edu.iuh.fit.se.messegeservice.event;

import java.time.Instant;

/**
 * Received from SocialService when a new post is created.
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
