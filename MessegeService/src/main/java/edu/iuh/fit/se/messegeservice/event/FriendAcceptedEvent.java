package edu.iuh.fit.se.messegeservice.event;

import java.time.Instant;

/**
 * Received from SocialService when a friend request is accepted.
 */
public record FriendAcceptedEvent(
        String userId1,
        String userId2,
        Instant occurredAt
) {
}
