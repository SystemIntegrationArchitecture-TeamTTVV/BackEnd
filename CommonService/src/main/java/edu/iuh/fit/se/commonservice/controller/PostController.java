package edu.iuh.fit.se.commonservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.iuh.fit.se.commonservice.dto.PostDTO;
import edu.iuh.fit.se.commonservice.service.PostService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<List<PostDTO>> getAllPosts() {
        return ResponseEntity.ok(postService.getAllPosts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostDTO> getPostById(@PathVariable String id) {
        return ResponseEntity.ok(postService.getPostById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostDTO>> getPostsByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(postService.getPostsByUserId(userId));
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
    public ResponseEntity<PostDTO> createPost(@RequestBody PostDTO postDTO) {
        // TODO: Extract userId from JWT token when security is re-enabled
        // For now, use hardcoded userId for testing
        if (postDTO.getAuthorId() == null) {
            postDTO.setAuthorId("696c5fe7781a5790a16c62e9"); // buihung's userId
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
    public ResponseEntity<PostDTO> sharePost(@PathVariable String id, @RequestBody PostDTO shareDTO) {
        // TODO: Extract userId from JWT token when security is re-enabled
        // For now, use hardcoded userId for testing
        if (shareDTO.getAuthorId() == null) {
            shareDTO.setAuthorId("696c5fe7781a5790a16c62e9"); // buihung's userId
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.sharePost(id, shareDTO));
    }
}

