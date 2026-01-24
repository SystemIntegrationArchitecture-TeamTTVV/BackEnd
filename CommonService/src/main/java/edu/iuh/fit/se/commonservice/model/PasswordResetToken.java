package edu.iuh.fit.se.commonservice.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "password_reset_tokens")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PasswordResetToken {
    @Id
    private String id;
    private String userId;
    private String token;
    private LocalDateTime expiryDate;
    private Boolean used;
    private LocalDateTime createdAt;
}
