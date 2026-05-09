package edu.iuh.fit.se.commonservice.controller;


import edu.iuh.fit.se.commonservice.dto.story.CreateStoryRequestDTO;
import edu.iuh.fit.se.commonservice.dto.story.StoryResponseDTO;

import edu.iuh.fit.se.commonservice.service.StoriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoriesController {

    private final StoriesService storiesService;

    /**
     * Lấy tất cả active stories chưa hết hạn
     * FE gọi: GET /api/stories
     */
    @GetMapping
    public List<StoryResponseDTO> getAllActiveStories() {
        return storiesService.getAllActiveStories();
    }

    /**
     * FE gọi:
     * GET /api/stories/feed/{userId}
     */
    @GetMapping("/feed/{userId}")
    public List<StoryResponseDTO> getStoryFeed(@PathVariable String userId) {
        return storiesService.getStoryFeed(userId);
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StoryResponseDTO createStory(
            @RequestParam("userId") String userId,
            @RequestParam("userName") String userName,
            @RequestParam("userAvatar") String userAvatar,
            @RequestParam("contentType") String contentType,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "background", required = false) String background,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        // Tạo DTO từ params
        CreateStoryRequestDTO data = CreateStoryRequestDTO.builder()
                .contentType(contentType)

                .content(content)
                .background(background)
                .caption(caption)
                .build();

        return storiesService.createStory(userId, userName, userAvatar, data, file);
    }

    /**
     * Mark story as viewed by current user.
     * POST /api/stories/{id}/view?userId={userId}
     */
    @PostMapping("/{id}/view")
    public StoryResponseDTO viewStory(
            @PathVariable String id,
            @RequestParam String userId) {
        return storiesService.viewStory(id, userId);
    }

    /**
     * Quick reaction to a story.
     * POST /api/stories/{id}/react?userId={userId}&emoji=like
     */
    @PostMapping("/{id}/react")
    public StoryResponseDTO reactStory(
            @PathVariable String id,
            @RequestParam String userId,
            @RequestParam String emoji) {
        return storiesService.reactStory(id, userId, emoji);
    }

}

