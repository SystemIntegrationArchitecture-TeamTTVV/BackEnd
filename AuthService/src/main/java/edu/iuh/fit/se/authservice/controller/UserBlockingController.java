package edu.iuh.fit.se.authservice.controller;

import edu.iuh.fit.se.authservice.dto.BlockedUserDTO;
import edu.iuh.fit.se.authservice.service.UserBlockingService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me/blocks")
@RequiredArgsConstructor
public class UserBlockingController {

    private final UserBlockingService userBlockingService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<?> getMyBlockedUsers(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String myUserId = extractUserId(authorization);
        if (myUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "unauthorized",
                    "message", "Valid bearer token is required"
            ));
        }

        try {
            List<BlockedUserDTO> data = userBlockingService.getMyBlockedUsers(myUserId);
            return ResponseEntity.ok(data);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "feature_disabled",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/{targetUserId}")
    public ResponseEntity<?> blockUser(@PathVariable String targetUserId,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        String myUserId = extractUserId(authorization);
        if (myUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "unauthorized",
                    "message", "Valid bearer token is required"
            ));
        }

        try {
            userBlockingService.blockUser(myUserId, targetUserId);
            return ResponseEntity.ok(Map.of("message", "User blocked"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "feature_disabled",
                    "message", e.getMessage()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "block_failed",
                    "message", e.getMessage() != null ? e.getMessage() : "Could not block user"
            ));
        }
    }

    @DeleteMapping("/{targetUserId}")
    public ResponseEntity<?> unblockUser(@PathVariable String targetUserId,
                                         @RequestHeader(value = "Authorization", required = false) String authorization) {
        String myUserId = extractUserId(authorization);
        if (myUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "unauthorized",
                    "message", "Valid bearer token is required"
            ));
        }

        try {
            userBlockingService.unblockUser(myUserId, targetUserId);
            return ResponseEntity.ok(Map.of("message", "User unblocked"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "feature_disabled",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/check")
    public ResponseEntity<?> checkBlocked(@RequestParam String userA, @RequestParam String userB) {
        try {
            boolean blocked = userBlockingService.isBlockedEitherDirection(userA, userB);
            return ResponseEntity.ok(Map.of("blocked", blocked));
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
        try {
            return jwtUtil.extractUserId(authorization.substring(7));
        } catch (Exception ignored) {
            return null;
        }
    }
}
