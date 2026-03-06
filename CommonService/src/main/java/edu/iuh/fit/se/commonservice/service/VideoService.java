package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.dto.VideoDTO;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.model.Video;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import edu.iuh.fit.se.commonservice.repository.VideoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final SocketService socketService;
    private final NotificationService notificationService;
    private final FriendService friendService;

    /**
     * Lấy tất cả videos (giống News Feed trên Facebook)
     */
    public List<VideoDTO> getAllVideos() {
        return videoRepository.findByIsDeletedFalseOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lấy videos phổ biến (theo view count)
     */
    public List<VideoDTO> getPopularVideos() {
        return videoRepository.findByIsDeletedFalseOrderByViewCountDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lấy video theo ID
     */
    public VideoDTO getVideoById(String id) {
        return videoRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Video not found with id: " + id));
    }

    /**
     * Lấy videos của một user
     */
    public List<VideoDTO> getVideosByUserId(String userId) {
        return videoRepository.findByAuthorIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lấy videos theo category
     */
    public List<VideoDTO> getVideosByCategory(String category) {
        return videoRepository.findByCategoryOrderByCreatedAtDesc(category).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Tạo video mới
     */
    public VideoDTO createVideo(VideoDTO videoDTO) {
        Video video = toEntity(videoDTO);
        video.setCreatedAt(LocalDateTime.now());
        video.setUpdatedAt(LocalDateTime.now());
        video.setDeleted(false);
        video.setViewCount(0);
        video.setLikeCount(0);
        video.setCommentCount(0);
        video.setShareCount(0);

        Video saved = videoRepository.save(video);
        VideoDTO savedDTO = toDTO(saved);

        // Thông báo cho bạn bè về video mới
        notifyFriendsAboutVideo(savedDTO);

        return savedDTO;
    }

    /**
     * Tăng view count khi user xem video
     */
    public VideoDTO incrementViewCount(String id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Video not found with id: " + id));

        video.setViewCount(video.getViewCount() + 1);
        Video updated = videoRepository.save(video);

        return toDTO(updated);
    }

    /**
     * Cập nhật video
     */
    public VideoDTO updateVideo(String id, VideoDTO videoDTO) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Video not found with id: " + id));

        video.setTitle(videoDTO.getTitle());
        video.setDescription(videoDTO.getDescription());
        video.setThumbnailUrl(videoDTO.getThumbnailUrl());
        video.setVisibility(videoDTO.getVisibility());
        video.setAllowComments(videoDTO.getAllowComments());
        video.setAllowReactions(videoDTO.getAllowReactions());
        video.setTags(videoDTO.getTags());
        video.setCategory(videoDTO.getCategory());
        video.setUpdatedAt(LocalDateTime.now());

        Video updated = videoRepository.save(video);
        return toDTO(updated);
    }

    /**
     * Xóa video (soft delete)
     */
    public void deleteVideo(String id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Video not found with id: " + id));

        video.setDeleted(true);
        video.setDeletedAt(LocalDateTime.now());
        videoRepository.save(video);
    }

    /**
     * Thông báo bạn bè về video mới
     */
    private void notifyFriendsAboutVideo(VideoDTO video) {
        try {
            User author = userRepository.findById(video.getAuthorId()).orElse(null);
            if (author == null) return;

            List<String> friendIds = friendService.getFriendsByUserId(video.getAuthorId())
                    .stream()
                    .map(friend -> friend.getFriendId())
                    .collect(Collectors.toList());

            for (String friendId : friendIds) {
                NotificationDTO notificationDTO = new NotificationDTO();
                notificationDTO.setType("VIDEO");
                notificationDTO.setActorId(video.getAuthorId());
                notificationDTO.setActorName(author.getFullName());
                notificationDTO.setActorAvatar(author.getAvatar());
                notificationDTO.setRecipientId(friendId);
                notificationDTO.setRelatedId(video.getId());
                notificationDTO.setRelatedType("VIDEO");
                notificationDTO.setTitle("Video mới");
                notificationDTO.setContent(author.getFullName() + " đã đăng video mới: " + video.getTitle());
                notificationDTO.setRead(false);
                notificationDTO.setCreatedAt(LocalDateTime.now());

                notificationService.createNotification(notificationDTO);
            }
        } catch (Exception e) {
            System.err.println("Failed to notify friends about video: " + e.getMessage());
        }
    }

    private VideoDTO toDTO(Video video) {
        VideoDTO dto = new VideoDTO();
        dto.setId(video.getId());
        if (video.getAuthor() != null) {
            dto.setAuthorId(video.getAuthor().getId());
            dto.setAuthorName(video.getAuthor().getFullName());
            dto.setAuthorAvatar(video.getAuthor().getAvatar());
        }
        dto.setTitle(video.getTitle());
        dto.setDescription(video.getDescription());
        dto.setVideoUrl(video.getVideoUrl());
        dto.setThumbnailUrl(video.getThumbnailUrl());
        dto.setDuration(video.getDuration());
        dto.setQuality(video.getQuality());
        dto.setFileSize(video.getFileSize());
        dto.setVisibility(video.getVisibility());
        dto.setAllowComments(video.getAllowComments());
        dto.setAllowReactions(video.getAllowReactions());
        dto.setViewCount(video.getViewCount());
        dto.setLikeCount(video.getLikeCount());
        dto.setCommentCount(video.getCommentCount());
        dto.setShareCount(video.getShareCount());
        if (video.getGroup() != null) {
            dto.setGroupId(video.getGroup().getId());
        }
        if (video.getPage() != null) {
            dto.setPageId(video.getPage().getId());
        }
        dto.setTags(video.getTags());
        dto.setCategory(video.getCategory());
        dto.setCreatedAt(video.getCreatedAt());
        dto.setUpdatedAt(video.getUpdatedAt());
        return dto;
    }

    private Video toEntity(VideoDTO dto) {
        Video video = new Video();
        if (dto.getAuthorId() != null) {
            User author = userRepository.findById(dto.getAuthorId())
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + dto.getAuthorId()));
            video.setAuthor(author);
        }
        video.setTitle(dto.getTitle());
        video.setDescription(dto.getDescription());
        video.setVideoUrl(dto.getVideoUrl());
        video.setThumbnailUrl(dto.getThumbnailUrl());
        video.setDuration(dto.getDuration());
        video.setQuality(dto.getQuality());
        video.setFileSize(dto.getFileSize());
        video.setVisibility(dto.getVisibility() != null ? dto.getVisibility() : "PUBLIC");
        video.setAllowComments(dto.getAllowComments() != null ? dto.getAllowComments() : true);
        video.setAllowReactions(dto.getAllowReactions() != null ? dto.getAllowReactions() : true);
        video.setTags(dto.getTags());
        video.setCategory(dto.getCategory());
        return video;
    }
}