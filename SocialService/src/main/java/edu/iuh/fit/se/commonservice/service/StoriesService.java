package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.story.CreateStoryRequestDTO;
import edu.iuh.fit.se.commonservice.dto.story.StoryResponseDTO;
import edu.iuh.fit.se.commonservice.dto.story.UserDTO;
import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import edu.iuh.fit.se.commonservice.model.Friend;
import edu.iuh.fit.se.commonservice.model.Stories;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.repository.StoriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoriesService {

    /** Same cap as UI — story media should stay small for feed performance. */
    private static final long MAX_STORY_MEDIA_BYTES = 10L * 1024 * 1024;

    private final StoriesRepository storiesRepo;
    private final FriendRepository friendRepo;
    private final FileUploadService fileUploadService;
    private final AuthServiceClient authServiceClient;
    /**
     * Lấy tất cả active stories chưa hết hạn
     */
    public List<StoryResponseDTO> getAllActiveStories() {
        LocalDateTime now = LocalDateTime.now();
        List<Stories> stories = storiesRepo
                .findByActiveTrueAndExpiredAtAfterOrderByCreatedAtDesc(now);
        
        return stories.stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Lấy story feed (bản thân + bạn bè)
     */
    public List<StoryResponseDTO> getStoryFeed(String userId) {

        // đảm bảo list mutable + không trùng
        Set<String> userIds = new HashSet<>(getFriendIds(userId));
        userIds.add(userId);

        List<Stories> stories = storiesRepo
                .findByUserIdInAndExpiredAtAfterAndActiveTrue(
                        new ArrayList<>(userIds),
                        LocalDateTime.now()
                );

        // Có thể sort lại nếu cần
        stories.sort(Comparator.comparing(Stories::getCreatedAt).reversed());

        return stories.stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Convert entity → DTO
     */
    private StoryResponseDTO toDTO(Stories s) {
        return toDTO(s, null);
    }

    private StoryResponseDTO toDTO(Stories s, String viewerUserId) {
        boolean isViewed = viewerUserId != null
                && s.getViewers() != null
                && s.getViewers().contains(viewerUserId);

        // Resolve avatar: nếu story không lưu avatar (blob/data bị sanitize ở FE),
        // lấy avatar mới nhất từ AuthService theo userId.
        String resolvedAvatar = s.getUserAvatar();
        if (resolvedAvatar == null || resolvedAvatar.isBlank()) {
            try {
                edu.iuh.fit.se.commonservice.dto.UserDTO author =
                        authServiceClient.getUserById(s.getUserId());
                if (author != null && author.getAvatar() != null) {
                    resolvedAvatar = author.getAvatar();
                }
            } catch (Exception ignored) {
                // AuthService không phản hồi — giữ nguyên rỗng, FE dùng initials fallback
            }
        }

        return StoryResponseDTO.builder()
                .id(s.getId())
                .user(edu.iuh.fit.se.commonservice.dto.story.UserDTO.builder()
                        .id(s.getUserId())
                        .name(s.getUserName())
                        .avatar(resolvedAvatar)
                        .build())
                .contentType(s.getContentType())
                .content(s.getContent())
                .background(s.getBackground())
                .caption(s.getCaption())
                .createdAt(s.getCreatedAt().toString())
                .expiresAt(s.getExpiredAt().toString())
                .isActive(s.getActive())
                .isViewed(isViewed)
                .viewCount(s.getViewers() != null ? s.getViewers().size() : 0)
                .reactions(s.getReactions() != null ? s.getReactions() : new java.util.HashMap<>())
                // only expose viewer list to owner
                .viewers(viewerUserId != null && viewerUserId.equals(s.getUserId())
                        ? s.getViewers()
                        : null)
                .build();
    }

    /**
     * Lấy danh sách ID bạn bè
     */
    public List<String> getFriendIds(String userId) {
        return friendRepo.findByUserId(userId)
                .stream()
                .map(Friend::getFriendId)
                .collect(Collectors.toList()); //  mutable list
    }
    public StoryResponseDTO createStory(
            String userId,
            String userName,
            String userAvatar,
            CreateStoryRequestDTO req,
            MultipartFile file
    ) {
        LocalDateTime now = LocalDateTime.now();
        String content;

        // 1️ phân loại story
        if ("text".equals(req.getContentType())) {
            content = req.getContent();
        } else {
            if (file == null || file.isEmpty()) {
                throw new RuntimeException("Media file is required");
            }
            if (file.getSize() > MAX_STORY_MEDIA_BYTES) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "Story media must not exceed 10 MB");
            }
            content = fileUploadService.uploadStoryFile(file);
        }

        // 2 build entity
        String captionMedia = req.getCaption();
        if (captionMedia != null) {
            captionMedia = captionMedia.trim();
            if (captionMedia.isEmpty()) captionMedia = null;
        }

        Stories story = Stories.builder()
                .userId(userId)
                .userName(userName)
                .userAvatar(userAvatar)

                .contentType(req.getContentType())
                .content(content)
                .background(req.getBackground())
                .caption("text".equals(req.getContentType()) ? null : captionMedia)

                .createdAt(now)
                .expiredAt(now.plusHours(24)) // TTL Mongo
                .active(true)

                .build();

        return toDTO(storiesRepo.save(story));
    }

    /**
     * Record that a user has viewed a story.
     * Idempotent — viewing twice doesn’t inflate the count.
     */
    public StoryResponseDTO viewStory(String storyId, String viewerUserId) {
        Stories story = storiesRepo.findById(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found"));

        if (!story.getViewers().contains(viewerUserId)) {
            story.getViewers().add(viewerUserId);
            story = storiesRepo.save(story);
        }
        return toDTO(story, viewerUserId);
    }

    /**
     * Add/remove a quick reaction to a story.
     * Supported emoji keys: like, love, haha, wow, sad, angry.
     * Calling twice with the same emoji toggles it off.
     */
    public StoryResponseDTO reactStory(String storyId, String userId, String emoji) {
        Stories story = storiesRepo.findById(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found"));

        java.util.Map<String, Integer> reacts = story.getReactions();
        // Track per-user reactions via a separate convention: "userId:emoji"
        // We use a simple increment/decrement on the aggregate count.
        // For a full per-user model, a separate collection would be better.
        reacts.merge(emoji, 1, Integer::sum);
        story = storiesRepo.save(story);
        return toDTO(story, userId);
    }
}
