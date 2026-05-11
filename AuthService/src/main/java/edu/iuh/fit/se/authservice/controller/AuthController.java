package edu.iuh.fit.se.authservice.controller;

import edu.iuh.fit.se.authservice.dto.AuthRequestDTO;
import edu.iuh.fit.se.authservice.dto.AuthResponseDTO;
import edu.iuh.fit.se.authservice.dto.ForgotPasswordRequestDTO;
import edu.iuh.fit.se.authservice.dto.ResetPasswordRequestDTO;
import edu.iuh.fit.se.authservice.dto.UserDTO;
import edu.iuh.fit.se.authservice.dto.VerifyOtpRequestDTO;
import edu.iuh.fit.se.authservice.dto.VerifyOtpResponseDTO;
import edu.iuh.fit.se.authservice.service.AuthenticationService;
import edu.iuh.fit.se.authservice.service.RecaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final RecaptchaService recaptchaService;

    @GetMapping("/captcha/challenge")
    public ResponseEntity<?> captchaChallenge() {
        return ResponseEntity.ok(recaptchaService.generateChallenge());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequestDTO request) {
        if (!recaptchaService.verify(request.getCaptchaToken(), request.getCaptchaText())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "captcha_failed",
                    "message", "Xác minh CAPTCHA không hợp lệ. Vui lòng thử lại."
            ));
        }
        try {
            return ResponseEntity.ok(authenticationService.login(request));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && (e.getMessage().contains("Invalid username or password")
                    || e.getMessage().contains("User not found"))) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "error", "Invalid username or password",
                        "message", "The username or password you entered is incorrect"
                ));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "An error occurred",
                    "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
            ));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody UserDTO userDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authenticationService.register(userDTO));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        try {
            return ResponseEntity.ok(authenticationService.refreshAccessToken(refreshToken));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody Map<String, String> body,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authHeader) {
        String refreshToken = body.get("refreshToken");
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            authenticationService.logout(refreshToken, accessToken);
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequestDTO request) {
        try {
            authenticationService.requestPasswordReset(request.getEmail());
        } catch (Exception ignored) {
            // Do not reveal whether email exists
        }
        return ResponseEntity.ok(Map.of(
                "message", "Nếu email tồn tại, mã xác minh đã được gửi đến hộp thư của bạn."
        ));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpRequestDTO request) {
        try {
            VerifyOtpResponseDTO response = authenticationService.verifyOtp(request.getEmail(), request.getOtp());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Xác minh thất bại",
                    "message", e.getMessage() != null ? e.getMessage() : "Mã xác minh không hợp lệ"
            ));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequestDTO request) {
        try {
            authenticationService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of(
                    "message", "Password has been reset successfully. You can now login with your new password."
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Reset failed",
                    "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
            ));
        }
    }
}
