package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LiveStreamApproveRequest {
    private String hostUserId;
    private String viewerUserId;
}
