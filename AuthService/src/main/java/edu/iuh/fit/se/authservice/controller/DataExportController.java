package edu.iuh.fit.se.authservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.iuh.fit.se.authservice.dto.UserDTO;
import edu.iuh.fit.se.authservice.entity.UserEntity;
import edu.iuh.fit.se.authservice.mapper.UserMapper;
import edu.iuh.fit.se.authservice.repository.UserRepository;
import edu.iuh.fit.se.authservice.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class DataExportController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Value("${app.features.data-export.enabled:false}")
    private boolean featureEnabled;

    /**
     * GET /api/users/me/export
     * Returns the user's own profile data as a JSON file download.
     * Header: Authorization: Bearer <token>
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportMyData(
            @RequestHeader("Authorization") String authHeader
    ) throws Exception {
        if (!featureEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Data export feature is disabled");
        }

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtUtil.extractUserId(token);
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserDTO dto = userMapper.toDto(user);

        // Build export payload — exclude sensitive fields
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("id", dto.getId());
        payload.put("username", dto.getUsername());
        payload.put("email", dto.getEmail());
        payload.put("fullName", dto.getFullName());
        payload.put("firstName", dto.getFirstName());
        payload.put("lastName", dto.getLastName());
        payload.put("avatar", dto.getAvatar());
        payload.put("bio", dto.getBio());
        payload.put("phoneNumber", dto.getPhoneNumber());
        payload.put("dateOfBirth", dto.getDateOfBirth());
        payload.put("gender", dto.getGender());
        payload.put("city", dto.getCity());
        payload.put("country", dto.getCountry());
        payload.put("workPlace", dto.getWorkPlace());
        payload.put("education", dto.getEducation());
        payload.put("relationshipStatus", dto.getRelationshipStatus());
        payload.put("interests", dto.getInterests());
        payload.put("profileVisibility", dto.getProfileVisibility());
        payload.put("postVisibility", dto.getPostVisibility());
        payload.put("statusText", dto.getStatusText());
        payload.put("statusEmoji", dto.getStatusEmoji());
        payload.put("createdAt", dto.getCreatedAt());

        byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(payload)
                .getBytes(StandardCharsets.UTF_8);

        String filename = "ttvv-data-export-" + dto.getUsername() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(jsonBytes.length)
                .body(jsonBytes);
    }
}
