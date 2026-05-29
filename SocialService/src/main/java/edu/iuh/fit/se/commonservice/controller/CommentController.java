package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.CommentDTO;
import edu.iuh.fit.se.commonservice.service.CommentQueryService;
import edu.iuh.fit.se.commonservice.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final CommentQueryService commentQueryService;

    @GetMapping("/post/{postId}")
    public ResponseEntity<List<CommentDTO>> getCommentsByPostId(@PathVariable String postId) {
        return ResponseEntity.ok(commentQueryService.getCommentsByPostId(postId));
    }

    @GetMapping("/parent/{parentCommentId}/replies")
    public ResponseEntity<List<CommentDTO>> getRepliesByParentCommentId(@PathVariable String parentCommentId) {
        return ResponseEntity.ok(commentQueryService.getRepliesByParentCommentId(parentCommentId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommentDTO> getCommentById(@PathVariable String id) {
        return ResponseEntity.ok(commentQueryService.getCommentById(id));
    }

    @PostMapping
    public ResponseEntity<CommentDTO> createComment(@RequestBody CommentDTO commentDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.createComment(commentDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommentDTO> updateComment(@PathVariable String id, @RequestBody CommentDTO commentDTO) {
        return ResponseEntity.ok(commentService.updateComment(id, commentDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable String id) {
        commentService.deleteComment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/video/{videoId}")
    public ResponseEntity<List<CommentDTO>> getCommentsByVideoId(@PathVariable String videoId) {
        return ResponseEntity.ok(commentService.getCommentsByVideoId(videoId));
    }

}

