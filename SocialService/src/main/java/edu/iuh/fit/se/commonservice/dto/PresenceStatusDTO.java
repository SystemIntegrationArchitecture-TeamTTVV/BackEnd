package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresenceStatusDTO {
    private String userId;
    private String username;
    private boolean online;
    private LocalDateTime lastSeenAt;
}
