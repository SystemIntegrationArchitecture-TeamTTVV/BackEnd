package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private String id;
    private String email;
    private String username;
    private String password; // Only used for registration
    private String firstName;
    private String lastName;
    private String fullName;
    private String avatar;
    private String coverPhoto;
    private String bio;
    private String phoneNumber;
    private LocalDateTime dateOfBirth;
    private String gender;
    private String city;
    private String country;
    private String workPlace;
    private String education;
    private String relationshipStatus;
    private Boolean isActive;
    private Boolean isVerified;
    private String role;
    private List<String> interests;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String profileVisibility;
    private String postVisibility;
    private Boolean showEmail;
    private Boolean showPhone;
}

