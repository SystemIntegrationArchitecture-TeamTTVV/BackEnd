package edu.iuh.fit.se.commonservice.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.iuh.fit.se.commonservice.dto.AuthRequestDTO;
import edu.iuh.fit.se.commonservice.dto.AuthResponseDTO;
import edu.iuh.fit.se.commonservice.dto.ForgotPasswordRequestDTO;
import edu.iuh.fit.se.commonservice.dto.ResetPasswordRequestDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.service.AuthService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AuthRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody UserDTO userDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(userDTO));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequestDTO request) {
        System.out.println("🔵 [AuthController] Forgot password request received for email: " + request.getEmail());
        try {
            authService.requestPasswordReset(request.getEmail());
            System.out.println("✅ [AuthController] Password reset request processed successfully");
            return ResponseEntity.ok(Map.of(
                    "message", "If an account exists with this email, you will receive password reset instructions."
            ));
        } catch (Exception e) {
            System.err.println("❌ [AuthController] Error processing password reset: " + e.getMessage());
            e.printStackTrace();
            // Don't reveal whether email exists or not for security
            return ResponseEntity.ok(Map.of(
                    "message", "If an account exists with this email, you will receive password reset instructions."
            ));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequestDTO request) {
        try {
            authService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of(
                    "message", "Password has been reset successfully. You can now login with your new password."
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "message", e.getMessage()
            ));
        }
    }
}
