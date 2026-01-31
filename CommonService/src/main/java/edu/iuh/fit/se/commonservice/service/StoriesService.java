package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.story.CreateStoryRequestDTO;
import edu.iuh.fit.se.commonservice.dto.story.StoryResponseDTO;
import edu.iuh.fit.se.commonservice.dto.story.UserDTO;
import edu.iuh.fit.se.commonservice.model.Friend;
import edu.iuh.fit.se.commonservice.model.Stories;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.repository.StoriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoriesService {

    private final StoriesRepository storiesRepo;
    private final FriendRepository friendRepo;
    private final FileUploadService fileUploadService;
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
        return StoryResponseDTO.builder()
                .id(s.getId())
                .user(UserDTO.builder()
                        .id(s.getUserId())
                        .name(s.getUserName())
                        .avatar(s.getUserAvatar())
                        .build())
                .contentType(s.getContentType())
                .content(s.getContent())
                .background(s.getBackground())
                .createdAt(s.getCreatedAt().toString())
                .expiresAt(s.getExpiredAt().toString())
                .isActive(s.getActive())
                .isViewed(false) // TODO: xử lý sau theo user
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
            content = fileUploadService.uploadStoryFile(file);
        }

        // 2 build entity
        Stories story = Stories.builder()
                .userId(userId)
                .userName(userName)
                .userAvatar(userAvatar)

                .contentType(req.getContentType())
                .content(content)
                .background(req.getBackground())

                .createdAt(now)
                .expiredAt(now.plusHours(24)) // TTL Mongo
                .active(true)

                .build();

        return toDTO(storiesRepo.save(story));
    }
}
