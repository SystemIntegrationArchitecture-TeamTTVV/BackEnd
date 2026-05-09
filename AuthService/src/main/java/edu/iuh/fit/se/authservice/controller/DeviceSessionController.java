package edu.iuh.fit.se.authservice.controller;

import edu.iuh.fit.se.authservice.dto.DeviceSessionDTO;
import edu.iuh.fit.se.authservice.service.DeviceSessionService;
import edu.iuh.fit.se.authservice.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me/sessions")
@RequiredArgsConstructor
public class DeviceSessionController {

    private final DeviceSessionService deviceSessionService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<?> getMySessions(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = extractUserId(authorization);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "unauthorized",
                    "message", "Valid bearer token is required"
            ));
        }

        try {
            List<DeviceSessionDTO> sessions = deviceSessionService.getMySessions(userId);
            return ResponseEntity.ok(sessions);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "feature_disabled",
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<?> revokeSession(@PathVariable String sessionId,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = extractUserId(authorization);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "unauthorized",
                    "message", "Valid bearer token is required"
            ));
        }

        try {
            deviceSessionService.revokeSession(userId, sessionId);
            return ResponseEntity.ok(Map.of("message", "Session revoked"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "feature_disabled",
                    "message", e.getMessage()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "revoke_failed",
                    "message", e.getMessage() != null ? e.getMessage() : "Could not revoke session"
            ));
        }
    }

    @PostMapping("/revoke-all")
    public ResponseEntity<?> revokeAllSessions(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = extractUserId(authorization);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "unauthorized",
                    "message", "Valid bearer token is required"
            ));
        }

        try {
            int revoked = deviceSessionService.revokeAllSessions(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "All sessions revoked",
                    "revoked", revoked
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "feature_disabled",
                    "message", e.getMessage()
            ));
        }
    }

    private String extractUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7);
        try {
            return jwtUtil.extractUserId(token);
        } catch (Exception ignored) {
            return null;
        }
    }
}
