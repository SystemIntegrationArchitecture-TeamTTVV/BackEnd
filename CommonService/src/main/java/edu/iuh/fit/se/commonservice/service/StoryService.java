package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.StoryDTO;
import edu.iuh.fit.se.commonservice.model.Story;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.StoryRepository;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoryService {

    private final StoryRepository storyRepository;
    private final UserRepository userRepository;

    public List<StoryDTO> getAllActiveStories() {
        LocalDateTime now = LocalDateTime.now();
        return storyRepository.findByIsActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(now).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<StoryDTO> getStoriesByAuthorId(String authorId) {
        return storyRepository.findByAuthorIdAndIsActiveTrueOrderByCreatedAtDesc(authorId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public StoryDTO getStoryById(String id) {
        return storyRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Story not found with id: " + id));
    }

    public StoryDTO createStory(StoryDTO storyDTO) {
        Story story = toEntity(storyDTO);
        story.setActive(true);
        story.setViewCount(0);
        story.setReactionCount(0);
        story.setCreatedAt(LocalDateTime.now());
        // Story expires after 24 hours
        story.setExpiresAt(LocalDateTime.now().plusHours(24));
        Story saved = storyRepository.save(story);
        return toDTO(saved);
    }

    public StoryDTO updateStory(String id, StoryDTO storyDTO) {
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Story not found with id: " + id));
        
        story.setText(storyDTO.getText());
        story.setBackgroundColor(storyDTO.getBackgroundColor());
        story.setVisibility(storyDTO.getVisibility());
        
        Story updated = storyRepository.save(story);
        return toDTO(updated);
    }

    public StoryDTO incrementViewCount(String id) {
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Story not found with id: " + id));
        story.setViewCount(story.getViewCount() + 1);
        Story updated = storyRepository.save(story);
        return toDTO(updated);
    }

    public void deleteStory(String id) {
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Story not found with id: " + id));
        story.setActive(false);
        storyRepository.save(story);
    }

    public void deleteExpiredStories() {
        storyRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    private StoryDTO toDTO(Story story) {
        StoryDTO dto = new StoryDTO();
        dto.setId(story.getId());
        if (story.getAuthor() != null) {
            dto.setAuthorId(story.getAuthor().getId());
            dto.setAuthorName(story.getAuthor().getFullName());
            dto.setAuthorAvatar(story.getAuthor().getAvatar());
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
        dto.setActive(story.isActive());
        return dto;
    }

    private Story toEntity(StoryDTO dto) {
        Story story = new Story();
        if (dto.getAuthorId() != null) {
            User author = userRepository.findById(dto.getAuthorId())
                    .orElseThrow(() -> new RuntimeException("Author not found"));
            story.setAuthor(author);
        }
        story.setType(dto.getType() != null ? dto.getType() : "IMAGE");
        story.setMediaUrl(dto.getMediaUrl());
        story.setThumbnailUrl(dto.getThumbnailUrl());
        story.setText(dto.getText());
        story.setBackgroundColor(dto.getBackgroundColor());
        story.setVisibility(dto.getVisibility() != null ? dto.getVisibility() : "PUBLIC");
        return story;
    }
}

