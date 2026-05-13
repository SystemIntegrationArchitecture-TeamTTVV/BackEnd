package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.PostDTO;
import edu.iuh.fit.se.commonservice.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<List<PostDTO>> getAdminPosts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(postService.getAdminPosts(status, sortBy, startDate, endDate));
    }

    @PutMapping("/{id}/hide")
    public ResponseEntity<PostDTO> toggleHidePost(@PathVariable String id) {
        return ResponseEntity.ok(postService.toggleHidePost(id));
    }

    @PutMapping("/{id}/lock-comments")
    public ResponseEntity<PostDTO> toggleLockComments(@PathVariable String id) {
        return ResponseEntity.ok(postService.toggleLockComments(id));
    }

    @PutMapping("/{id}/soft-delete")
    public ResponseEntity<PostDTO> softDeletePostWithReason(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "Vi phạm tiêu chuẩn cộng đồng");
        return ResponseEntity.ok(postService.softDeletePostWithReason(id, reason));
    }

    @PutMapping("/{id}/restore")
    public ResponseEntity<PostDTO> restorePost(@PathVariable String id) {
        return ResponseEntity.ok(postService.restorePost(id));
    }

    @DeleteMapping("/{id}/hard-delete")
    public ResponseEntity<Void> hardDeletePost(@PathVariable String id) {
        postService.hardDeletePost(id);
        return ResponseEntity.noContent().build();
    }
}
