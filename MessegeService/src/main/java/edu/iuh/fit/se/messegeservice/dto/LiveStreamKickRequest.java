package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LiveStreamKickRequest {
    private String hostUserId;
    /** identity LiveKit = userId viewer */
    private String participantUserId;
}
