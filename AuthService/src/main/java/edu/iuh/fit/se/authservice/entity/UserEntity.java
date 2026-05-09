package edu.iuh.fit.se.authservice.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    private String firstName;
    private String lastName;
    private String fullName;

    @Column(columnDefinition = "TEXT")
    private String avatar;

    @Column(columnDefinition = "TEXT")
    private String coverPhoto;

    @Column(columnDefinition = "TEXT")
    private String bio;
    private String phoneNumber;
    private LocalDateTime dateOfBirth;
    private String gender;
    private String city;
    private String country;
    private String workPlace;
    private String education;
    private String relationshipStatus;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean verified = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private RoleEntity role;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_interests", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "interest")
    @Builder.Default
    private List<String> interests = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private String profileVisibility = "PUBLIC";
    @Builder.Default
    private String postVisibility = "PUBLIC";
    @Builder.Default
    private Boolean showEmail = false;
    @Builder.Default
    private Boolean showPhone = false;

    @Builder.Default
    private String allowMessageFrom = "EVERYONE";
    @Builder.Default
    private String allowCallFrom = "EVERYONE";
    @Builder.Default
    private String allowGroupInviteFrom = "EVERYONE";

    /** User-set status text (max 80 chars) */
    @Column(length = 80)
    private String statusText;

    /** Emoji code for status, e.g. "😊" */
    @Column(length = 8)
    private String statusEmoji;

    /** TOTP secret for 2FA (null = 2FA disabled) */
    @Column(name = "totp_secret", length = 64)
    private String totpSecret;

    /** Whether 2FA is confirmed and active */
    @Column(name = "totp_enabled", nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean totpEnabled = false;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (interests == null) {
            interests = new ArrayList<>();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
