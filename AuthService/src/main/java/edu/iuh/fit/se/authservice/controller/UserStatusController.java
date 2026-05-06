package edu.iuh.fit.se.authservice.controller;

import edu.iuh.fit.se.authservice.dto.UserDTO;
import edu.iuh.fit.se.authservice.dto.UserStatusRequest;
import edu.iuh.fit.se.authservice.entity.UserEntity;
import edu.iuh.fit.se.authservice.mapper.UserMapper;
import edu.iuh.fit.se.authservice.repository.UserRepository;
import edu.iuh.fit.se.authservice.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserStatusController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    @Value("${app.features.user-status.enabled:false}")
    private boolean featureEnabled;

    /** PATCH /api/users/me/status
     *  Header: Authorization: Bearer <token>
     */
    @PatchMapping("/status")
    public ResponseEntity<UserDTO> updateMyStatus(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody UserStatusRequest request
    ) {
        if (!featureEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "User status feature is disabled");
        }

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtUtil.extractUserId(token);
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Validate and sanitize inputs
        if (request.getStatusText() != null) {
            String text = request.getStatusText().strip();
            user.setStatusText(text.isEmpty() ? null : text);
        } else {
            user.setStatusText(null);
        }

        if (request.getStatusEmoji() != null) {
            String emoji = request.getStatusEmoji().strip();
            user.setStatusEmoji(emoji.isEmpty() ? null : emoji);
        } else {
            user.setStatusEmoji(null);
        }

        userRepository.save(user);
        return ResponseEntity.ok(userMapper.toDto(user));
    }
}
