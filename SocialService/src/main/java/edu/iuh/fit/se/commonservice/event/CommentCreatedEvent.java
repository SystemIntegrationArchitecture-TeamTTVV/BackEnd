package edu.iuh.fit.se.commonservice.event;

import java.time.Instant;

/**
 * Published when a comment is written.
 * Processed asynchronously to save to DB and update comment count.
 */
public record CommentCreatedEvent(
        String id, // comment ID if pre-generated, or null
        String postId,
        String authorId,
        String content,
        String parentId, // for replies
        Instant occurredAt
) {
}
