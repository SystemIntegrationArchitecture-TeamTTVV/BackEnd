package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String email;
    
    @Indexed(unique = true)
    private String username;
    
    private String password;
    private String firstName;
    private String lastName;
    private String fullName;
    private String avatar; // URL ảnh đại diện
    private String coverPhoto; // URL ảnh bìa
    private String bio; // Giới thiệu bản thân
    private String phoneNumber;
    private LocalDateTime dateOfBirth;
    private String gender; // Nam, Nữ, Khác
    private String city; // Thành phố
    private String country; // Quốc gia
    private String workPlace; // Nơi làm việc
    private String education; // Học vấn
    private String relationshipStatus; // Độc thân, Đã kết hôn, v.v.
    
    private Boolean isActive = true;
    private Boolean isVerified = false;
    
    @DBRef
    private Role role; // Reference to Role entity
    private String roleId; // For easier querying
    
    private List<String> interests; // Sở thích
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Privacy settings
    private String profileVisibility = "PUBLIC"; // PUBLIC, FRIENDS, PRIVATE
    private String postVisibility = "PUBLIC"; // PUBLIC, FRIENDS, PRIVATE
    private Boolean showEmail = false;
    private Boolean showPhone = false;
}

