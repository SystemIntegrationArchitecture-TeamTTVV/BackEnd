package edu.iuh.fit.se.commonservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import edu.iuh.fit.se.commonservice.dto.VideoDTO;
import edu.iuh.fit.se.commonservice.service.VideoService;
import edu.iuh.fit.se.commonservice.service.FileUploadService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final FileUploadService fileUploadService;

    private static final long VIDEO_MAX_BYTES = 5L * 1024 * 1024; // 5 MB

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

    /**
     * Upload video file + tạo bản ghi video.
     * POST /api/videos/upload  (multipart/form-data)
     * Giới hạn: 5 MB — nếu vượt BE trả 413.
     */
    @PostMapping("/upload")
    public ResponseEntity<VideoDTO> uploadVideo(
            @RequestParam("authorId")  String authorId,
            @RequestParam("title")     String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "visibility",  defaultValue = "PUBLIC") String visibility,
            @RequestPart("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file is required");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("video/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only video files are accepted");
        }
        if (file.getSize() > VIDEO_MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Video must not exceed 5 MB");
        }

        String videoUrl = fileUploadService.uploadVideoFile(file);

        VideoDTO dto = new VideoDTO();
        dto.setAuthorId(authorId);
        dto.setTitle(title.trim());
        dto.setDescription(description != null ? description.trim() : null);
        dto.setVideoUrl(videoUrl);
        dto.setVisibility(visibility);
        dto.setFileSize(file.getSize());
        dto.setAllowComments(true);
        dto.setAllowReactions(true);

        return ResponseEntity.status(HttpStatus.CREATED).body(videoService.createVideo(dto));
    }

    @PostMapping
    public ResponseEntity<VideoDTO> createVideo(@RequestBody VideoDTO videoDTO) {
        if (videoDTO.getAuthorId() == null) {
            videoDTO.setAuthorId("696c5fe7781a5790a16c62e9");
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