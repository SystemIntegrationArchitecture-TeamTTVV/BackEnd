package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.ReactionDTO;
import edu.iuh.fit.se.commonservice.service.ReactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @GetMapping("/post/{postId}")
    public ResponseEntity<List<ReactionDTO>> getReactionsByPostId(@PathVariable String postId) {
        return ResponseEntity.ok(reactionService.getReactionsByPostId(postId));
    }

    @GetMapping("/comment/{commentId}")
    public ResponseEntity<List<ReactionDTO>> getReactionsByCommentId(@PathVariable String commentId) {
        return ResponseEntity.ok(reactionService.getReactionsByCommentId(commentId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ReactionDTO>> getReactionsByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(reactionService.getReactionsByUserId(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReactionDTO> getReactionById(@PathVariable String id) {
        return ResponseEntity.ok(reactionService.getReactionById(id));
    }

    @PostMapping
    public ResponseEntity<ReactionDTO> createReaction(@RequestBody ReactionDTO reactionDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reactionService.createReaction(reactionDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReaction(@PathVariable String id) {
        reactionService.deleteReaction(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/post/{postId}/user/{userId}")
    public ResponseEntity<Void> deleteReactionByPostIdAndUserId(
            @PathVariable String postId, 
            @PathVariable String userId) {
        reactionService.deleteReactionByPostIdAndUserId(postId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/comment/{commentId}/user/{userId}")
    public ResponseEntity<Void> deleteReactionByCommentIdAndUserId(
            @PathVariable String commentId, 
            @PathVariable String userId) {
        reactionService.deleteReactionByCommentIdAndUserId(commentId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/video/{videoId}")
    public ResponseEntity<List<ReactionDTO>> getReactionsByVideoId(@PathVariable String videoId) {
        return ResponseEntity.ok(reactionService.getReactionsByVideoId(videoId));
    }

    @DeleteMapping("/video/{videoId}/user/{userId}")
    public ResponseEntity<Void> deleteReactionByVideoIdAndUserId(
            @PathVariable String videoId,
            @PathVariable String userId) {
        reactionService.deleteReactionByVideoIdAndUserId(videoId, userId);
        return ResponseEntity.noContent().build();
    }
}

