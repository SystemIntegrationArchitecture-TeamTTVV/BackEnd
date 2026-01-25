package edu.iuh.fit.se.commonservice.controller;


import edu.iuh.fit.se.commonservice.dto.story.StoryResponseDTO;

import edu.iuh.fit.se.commonservice.service.StoriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoriesController {

    private final StoriesService storiesService;

    /**
     * FE gọi:
     * GET /api/stories/feed/{userId}
     */
    @GetMapping("/feed/{userId}")
    public List<StoryResponseDTO> getStoryFeed(@PathVariable String userId) {
        return storiesService.getStoryFeed(userId);
    }
}

