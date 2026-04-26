package edu.iuh.fit.se.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {
    private String token;
    private String refreshToken;
    private String username;
    private String role;
    private String userId;
    private String fullName;
    private String avatar;
}
