package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.FriendSuggestionDTO;
import edu.iuh.fit.se.commonservice.service.FriendSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendSuggestionController {

    private final FriendSuggestionService friendSuggestionService;

    @Value("${app.features.friend-suggestions.enabled:false}")
    private boolean featureEnabled;

    /**
     * GET /api/friends/suggestions?userId=...&limit=10
     * Returns ranked friend suggestions based on mutual friends.
     */
    @GetMapping("/suggestions")
    public ResponseEntity<List<FriendSuggestionDTO>> getSuggestions(
            @RequestParam String userId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (!featureEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Friend suggestions feature is disabled");
        }
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        int safeLimit = Math.min(Math.max(1, limit), 50);
        return ResponseEntity.ok(friendSuggestionService.getSuggestions(userId, safeLimit));
    }
}
