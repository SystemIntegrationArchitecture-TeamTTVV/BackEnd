package edu.iuh.fit.se.commonservice.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.iuh.fit.se.commonservice.dto.PostDTO;
import edu.iuh.fit.se.commonservice.service.SavedPostService;
import edu.iuh.fit.se.commonservice.service.UserIdentityService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class SavedPostController {

    private final SavedPostService savedPostService;
    private final UserIdentityService userIdentityService;

    @PostMapping("/{postId}/save")
    public ResponseEntity<Void> savePost(@PathVariable String postId) {
        String userId = resolveCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        
        savedPostService.savePost(userId, postId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{postId}/save")
    public ResponseEntity<Void> unsavePost(@PathVariable String postId) {
        String userId = resolveCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        
        savedPostService.unsavePost(userId, postId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/saved")
    public ResponseEntity<List<PostDTO>> getSavedPosts() {
        String userId = resolveCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        
        return ResponseEntity.ok(savedPostService.getSavedPosts(userId));
    }
    
    @GetMapping("/saved/ids")
    public ResponseEntity<List<String>> getSavedPostIds() {
        String userId = resolveCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        
        return ResponseEntity.ok(savedPostService.getSavedPostIds(userId));
    }

    private String resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return null;
        }

        String username = authentication.getName();
        return userIdentityService.findByUsername(username)
                .map(u -> u.getId())
                .orElse(null);
    }
}
