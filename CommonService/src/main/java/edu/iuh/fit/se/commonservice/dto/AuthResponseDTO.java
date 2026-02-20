package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {
    /**
     * Access token (JWT) used for authenticating API calls.
     */
    private String token;

    /**
     * Long-lived refresh token used to obtain new access tokens without re-login.
     */
    private String refreshToken;

    private String username;
    private String role;
    private String userId;
    private String fullName;
    private String avatar;
}

