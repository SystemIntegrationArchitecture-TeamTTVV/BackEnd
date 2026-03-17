package edu.iuh.fit.se.messegeservice.event;

import java.time.Instant;

public record UserRegisteredEvent(
        String userId,
        String username,
        Instant occurredAt
) {
}

