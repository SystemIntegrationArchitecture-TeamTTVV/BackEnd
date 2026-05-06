package edu.iuh.fit.se.authservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_sessions", indexes = {
        @Index(name = "idx_device_sessions_user_id", columnList = "userId"),
        @Index(name = "idx_device_sessions_refresh_token", columnList = "refreshToken", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String refreshToken;

    private String source;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    private String ipAddress;

    @Builder.Default
    @Column(nullable = false)
    private Boolean revoked = false;

    private LocalDateTime lastSeenAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
        updatedAt = now;
        if (revoked == null) {
            revoked = false;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
