package edu.iuh.fit.se.commonservice.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import edu.iuh.fit.se.commonservice.dto.PostDTO;
import edu.iuh.fit.se.commonservice.service.PostGroupService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/group-posts")
@RequiredArgsConstructor
public class PostGroupController {

    private final PostGroupService postService;

    // ================= GET =================

    //  Lấy tất cả bài trong group
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<PostDTO>> getPostsByGroup(
            @PathVariable String groupId
    ) {
        return ResponseEntity.ok(postService.getPostsByGroupId(groupId));
    }

    //  Lấy chi tiết bài viết
    @GetMapping("/{postId}")
    public ResponseEntity<PostDTO> getPostById(
            @PathVariable String postId
    ) {
        return ResponseEntity.ok(postService.getPostById(postId));
    }

    // ================= CREATE =================

    @PostMapping
    public ResponseEntity<PostDTO> createPost(
            @RequestBody PostDTO dto
    ) {
        return ResponseEntity.ok(postService.createPost(dto));
    }

    // ================= UPDATE =================

    @PutMapping("/{postId}")
    public ResponseEntity<PostDTO> updatePost(
            @PathVariable String postId,
            @RequestBody PostDTO dto
    ) {
        return ResponseEntity.ok(postService.updatePost(postId, dto));
    }

    // ================= DELETE =================

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable String postId
    ) {
        postService.deletePost(postId);
        return ResponseEntity.ok().build();
    }
}