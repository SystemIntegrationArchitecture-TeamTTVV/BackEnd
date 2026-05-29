package edu.iuh.fit.se.mediaservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LiveStreamSettingsRequest {
    private String hostUserId;
    private boolean requiresApproval;
}
