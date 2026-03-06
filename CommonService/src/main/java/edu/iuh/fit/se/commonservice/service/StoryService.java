package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.StoryDTO;
import edu.iuh.fit.se.commonservice.model.Story;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.StoryRepository;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StoryService {

    private final StoryRepository storyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final FriendService friendService;

    public List<StoryDTO> getAllActiveStories() {
        LocalDateTime now = LocalDateTime.now();
        System.out.println("🕐 [StoryService] Current server time: " + now);
        
        // Try both queries for debugging
        List<Story> storiesWithExpiry = storyRepository.findByActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(now);
        List<Story> allActiveStories = storyRepository.findByActiveTrueOrderByCreatedAtDesc();
        
        System.out.println("📖 [StoryService] Found " + storiesWithExpiry.size() + " stories (with expiry filter)");
        System.out.println("📖 [StoryService] Found " + allActiveStories.size() + " active stories (no expiry filter)");
        
        // Use the query without expiry filter for now
        List<Story> stories = allActiveStories;
        
        if (!stories.isEmpty()) {
            System.out.println("📄 [StoryService] First story: id=" + stories.get(0).getId() + 
                             ", active=" + stories.get(0).getActive() + 
                             ", expiresAt=" + stories.get(0).getExpiresAt());
        }
        
        return stories.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<StoryDTO> getStoriesByAuthorId(String authorId) {
        return storyRepository.findByAuthorIdAndActiveTrueOrderByCreatedAtDesc(authorId).stream()
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
        
        System.out.println("📝 [StoryService] Creating story: type=" + story.getType() + 
                           ", author=" + (story.getAuthor() != null ? story.getAuthor().getFullName() : "null") +
                           ", active=" + story.getActive() + 
                           ", expiresAt=" + story.getExpiresAt());
        
        Story saved = storyRepository.save(story);
        System.out.println("✅ [StoryService] Story saved with ID: " + saved.getId());
        
        StoryDTO savedDTO = toDTO(saved);
        
        // Notify friends about new story
        notifyFriendsAboutStory(savedDTO);
        
        return savedDTO;
    }
    
    private void notifyFriendsAboutStory(StoryDTO story) {
        try {
            // Get author info
            User author = userRepository.findById(story.getAuthorId()).orElse(null);
            if (author == null) return;
            
            // Get all friends
            List<String> friendIds = friendService.getFriendsByUserId(story.getAuthorId())
                    .stream()
                    .map(friend -> friend.getFriendId())
                    .collect(Collectors.toList());
            
            // Create notification for each friend
            for (String friendId : friendIds) {
                NotificationDTO notificationDTO = new NotificationDTO();
                notificationDTO.setType("STORY");
                notificationDTO.setActorId(story.getAuthorId());
                notificationDTO.setActorName(author.getFullName());
                notificationDTO.setActorAvatar(author.getAvatar());
                notificationDTO.setRecipientId(friendId);
                notificationDTO.setRelatedId(story.getId());
                notificationDTO.setRelatedType("STORY");
                notificationDTO.setTitle("Story mới");
                notificationDTO.setContent(author.getFullName() + " đã đăng story mới");
                notificationDTO.setRead(false);
                notificationDTO.setCreatedAt(LocalDateTime.now());
                
                notificationService.createNotification(notificationDTO);
            }
        } catch (Exception e) {
            System.err.println("Failed to notify friends about story: " + e.getMessage());
        }
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
        story.setViewCount((story.getViewCount() != null ? story.getViewCount() : 0) + 1);
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
        dto.setActive(story.getActive());
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

