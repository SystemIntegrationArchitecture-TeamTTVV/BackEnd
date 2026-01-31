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
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        // Tạo DTO từ params
        CreateStoryRequestDTO data = CreateStoryRequestDTO.builder()
                .contentType(contentType)

                .content(content)
                .background(background)
                .build();

        return storiesService.createStory(userId, userName, userAvatar, data, file);
    }

}

