package edu.iuh.fit.se.commonservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import edu.iuh.fit.se.commonservice.dto.PostDTO;
import edu.iuh.fit.se.commonservice.service.PostQueryService;
import edu.iuh.fit.se.commonservice.service.PostService;
import edu.iuh.fit.se.commonservice.service.UserIdentityService;
import edu.iuh.fit.se.commonservice.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PostQueryService postQueryService;
    private final JwtUtil jwtUtil;
    private final UserIdentityService userIdentityService;

    @GetMapping
    public ResponseEntity<List<PostDTO>> getAllPosts(@RequestParam(required = false) String viewerId) {
        return ResponseEntity.ok(postQueryService.getAllPosts(viewerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostDTO> getPostById(@PathVariable String id) {
        return ResponseEntity.ok(postQueryService.getPostById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostDTO>> getPostsByUserId(
            @PathVariable String userId,
            @RequestParam(required = false) String viewerId) {
        return ResponseEntity.ok(postQueryService.getPostsByUserId(userId, viewerId));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<PostDTO>> getPostsByGroupId(@PathVariable String groupId) {
        return ResponseEntity.ok(postService.getPostsByGroupId(groupId));
    }

    @GetMapping("/page/{pageId}")
    public ResponseEntity<List<PostDTO>> getPostsByPageId(@PathVariable String pageId) {
        return ResponseEntity.ok(postService.getPostsByPageId(pageId));
    }

    @PostMapping
    public ResponseEntity<PostDTO> createPost(@RequestBody PostDTO postDTO, HttpServletRequest request) {
        if (postDTO.getAuthorId() == null) {
            postDTO.setAuthorId(resolveCurrentUserId(request));
        }

        if (postDTO.getAuthorId() == null || postDTO.getAuthorId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "authorId is required");
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(postDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostDTO> updatePost(@PathVariable String id, @RequestBody PostDTO postDTO) {
        return ResponseEntity.ok(postService.updatePost(id, postDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable String id) {
        postService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<PostDTO> sharePost(@PathVariable String id, @RequestBody PostDTO shareDTO, HttpServletRequest request) {
        if (shareDTO.getAuthorId() == null) {
            shareDTO.setAuthorId(resolveCurrentUserId(request));
        }

        if (shareDTO.getAuthorId() == null || shareDTO.getAuthorId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "authorId is required");
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.sharePost(id, shareDTO));
    }

    private String resolveCurrentUserId(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String userId = jwtUtil.extractClaim(token, claims -> claims.get("userId", String.class));
                if (userId != null && !userId.isBlank()) {
                    return userId;
                }
            }
        } catch (Exception ignored) {
        }

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

