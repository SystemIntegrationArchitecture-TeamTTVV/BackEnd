package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.story.StoryResponseDTO;
import edu.iuh.fit.se.commonservice.dto.story.UserDTO;
import edu.iuh.fit.se.commonservice.model.Friend;
import edu.iuh.fit.se.commonservice.model.Stories;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.repository.StoriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoriesService {

    private final StoriesRepository storiesRepo;
    private final FriendRepository friendRepo;

    /**
     * Lấy story feed (bản thân + bạn bè)
     */
    public List<StoryResponseDTO> getStoryFeed(String userId) {

        // ✅ đảm bảo list mutable + không trùng
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
                .duration(s.getDuration())
                .createdAt(s.getCreatedAt().toString())
                .expiresAt(s.getExpiredAt().toString())
                .viewCount(s.getViewCount())
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
                .collect(Collectors.toList()); // ✅ mutable list
    }
}
