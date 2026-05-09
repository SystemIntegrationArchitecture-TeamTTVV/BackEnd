package edu.iuh.fit.se.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceSessionDTO {

    private String id;
    private String username;
    private String source;
    private String userAgent;
    private String ipAddress;
    private Boolean revoked;
    private LocalDateTime lastSeenAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
}
