package edu.iuh.fit.se.commonservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import edu.iuh.fit.se.commonservice.dto.VideoDTO;
import edu.iuh.fit.se.commonservice.service.VideoService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @GetMapping
    public ResponseEntity<List<VideoDTO>> getAllVideos() {
        return ResponseEntity.ok(videoService.getAllVideos());
    }

    @GetMapping("/popular")
    public ResponseEntity<List<VideoDTO>> getPopularVideos() {
        return ResponseEntity.ok(videoService.getPopularVideos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VideoDTO> getVideoById(@PathVariable String id) {
        return ResponseEntity.ok(videoService.getVideoById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<VideoDTO>> getVideosByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(videoService.getVideosByUserId(userId));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<VideoDTO>> getVideosByCategory(@PathVariable String category) {
        return ResponseEntity.ok(videoService.getVideosByCategory(category));
    }

    @PostMapping
    public ResponseEntity<VideoDTO> createVideo(@RequestBody VideoDTO videoDTO) {
        if (videoDTO.getAuthorId() == null) {
            videoDTO.setAuthorId("696c5fe7781a5790a16c62e9"); // Default user for testing
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(videoService.createVideo(videoDTO));
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<VideoDTO> incrementViewCount(@PathVariable String id) {
        return ResponseEntity.ok(videoService.incrementViewCount(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VideoDTO> updateVideo(@PathVariable String id, @RequestBody VideoDTO videoDTO) {
        return ResponseEntity.ok(videoService.updateVideo(id, videoDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVideo(@PathVariable String id) {
        videoService.deleteVideo(id);
        return ResponseEntity.noContent().build();
    }
}