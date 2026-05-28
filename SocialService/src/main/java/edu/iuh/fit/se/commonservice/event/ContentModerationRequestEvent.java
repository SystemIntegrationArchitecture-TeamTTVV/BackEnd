package edu.iuh.fit.se.commonservice.event;

import java.time.Instant;

/**
 * Published when content (post/comment) needs AI moderation.
 * Consumed by a background worker that calls Gemini API asynchronously.
 */
public record ContentModerationRequestEvent(
        String contentId,
        String contentType,
        String authorId,
        String text,
        Instant occurredAt
) {
}
