package edu.iuh.fit.se.commonservice.event;

import java.time.Instant;

/**
 * Published when a friend request is accepted.
 * Consumed by MessegeService to auto-create a Direct conversation.
 */
public record FriendAcceptedEvent(
        String userId1,
        String userId2,
        Instant occurredAt
) {
}
