package edu.iuh.fit.se.commonservice.event;

import java.time.Instant;

/**
 * Published when a user likes or unlikes a post.
 * Processed asynchronously to aggregate like count.
 */
public record PostLikedEvent(
        String postId,
        String userId,
        boolean liked, // true for like, false for unlike
        Instant occurredAt
) {
}
