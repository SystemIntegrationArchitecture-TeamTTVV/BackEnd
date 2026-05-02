package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LiveStreamSettingsRequest {
    private String hostUserId;
    private boolean requiresApproval;
}
