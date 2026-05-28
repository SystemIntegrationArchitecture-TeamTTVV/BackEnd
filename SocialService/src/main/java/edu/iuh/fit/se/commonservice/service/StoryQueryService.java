package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.StoryDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.model.Story;
import edu.iuh.fit.se.commonservice.repository.StoryRepository;
import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CQRS - Query Service for Stories.
 * Optimized for heavy read load using Redis Caching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoryQueryService {

    private final StoryRepository storyRepository;
    private final AuthServiceClient authServiceClient;

    @Cacheable(value = "stories-feed", key = "'all'")
    public List<StoryDTO> getAllActiveStories() {
        log.info("[CQRS-Query] Cache miss. Fetching all active stories from DB");
        List<Story> stories = storyRepository.findByActiveTrueOrderByCreatedAtDesc();
        return stories.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private StoryDTO toDTO(Story story) {
        StoryDTO dto = new StoryDTO();
        dto.setId(story.getId());
        if (story.getAuthorId() != null) {
            dto.setAuthorId(story.getAuthorId());
            try {
                UserDTO author = authServiceClient.getUserById(story.getAuthorId());
                dto.setAuthorName(author.getFullName() != null ? author.getFullName() : author.getUsername());
                dto.setAuthorAvatar(author.getAvatar());
            } catch (Exception e) {
                dto.setAuthorName("Unknown");
            }
        }
        dto.setType(story.getType());
        dto.setMediaUrl(story.getMediaUrl());
        dto.setThumbnailUrl(story.getThumbnailUrl());
        dto.setText(story.getText());
        dto.setBackgroundColor(story.getBackgroundColor());
        dto.setVisibility(story.getVisibility());
        dto.setViewCount(story.getViewCount());
        dto.setReactionCount(story.getReactionCount());
        dto.setCreatedAt(story.getCreatedAt());
        dto.setExpiresAt(story.getExpiresAt());
        dto.setActive(story.getActive());
        return dto;
    }
}
