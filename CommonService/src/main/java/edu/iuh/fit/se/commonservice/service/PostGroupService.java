package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import edu.iuh.fit.se.commonservice.repository.PostRepository;
import org.springframework.stereotype.Service;

import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.PostDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.model.Group;
import edu.iuh.fit.se.commonservice.model.GroupMember;
import edu.iuh.fit.se.commonservice.model.Post; // <- đổi từ PostGroup
import edu.iuh.fit.se.commonservice.model.User;

import edu.iuh.fit.se.commonservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostGroupService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final SocketService socketService;
    private final NotificationService notificationService;
    private final GroupMemberService groupMemberService;

    // ================= GET =================

    public List<PostDTO> getPostsByGroupId(String groupId) {
        return getPostsByGroupId(groupId, null);
    }

    public List<PostDTO> getPostsByGroupId(String groupId, String viewerId) {
        return postRepository
                .findByGroupIdAndNotDeleted(groupId)
                .stream()
                .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // newest first
                .map(post -> toDTOForViewer(post, viewerId))
                .collect(Collectors.toList());
    }

    public PostDTO getPostById(String id) {
        return getPostById(id, null);
    }

    public PostDTO getPostById(String id, String viewerId) {
        return postRepository.findById(id)
                .map(post -> toDTOForViewer(post, viewerId))
                .orElseThrow(() -> new RuntimeException("Post not found"));
    }

    // ================= CREATE =================

    public PostDTO createPost(PostDTO dto) {

        // 🔥 check user có trong group không
        boolean isMember = groupMemberService.getMembers(dto.getGroupId())
                .stream()
                .anyMatch(m -> m.getUserId().equals(dto.getAuthorId())
                        && "ACTIVE".equals(m.getStatus()));

        if (!isMember) {
            throw new RuntimeException("User is not in group");
        }

        Post post = toEntity(dto); // <- đổi từ PostGroup

        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        post.setDeleted(false);

        Post saved = postRepository.save(post);
        PostDTO result = toDTO(saved);

        // 🔥 notify member
        notifyGroupMembers(result);

        // 🔥 realtime socket
        socketService.notifyPostCreated(
                result.getGroupId(),
                SocketEventDTO.postCreated(result.getAuthorId(), result)
        );

        return result;
    }

    // ================= NOTIFY =================

    private void notifyGroupMembers(PostDTO post) {

        if (post.getGroupId() == null) return;

        User author = userRepository.findById(post.getAuthorId()).orElse(null);
        if (author == null) return;

        List<String> memberIds = groupMemberService.getMembers(post.getGroupId())
                .stream()
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .map(GroupMember::getUserId)
                .collect(Collectors.toList());

        for (String memberId : memberIds) {

            if (memberId.equals(post.getAuthorId())) continue;

            NotificationDTO noti = new NotificationDTO();
            noti.setRecipientId(memberId);

            noti.setActorId(post.getAuthorId());
            noti.setActorName(author.getFullName());
            noti.setActorAvatar(author.getAvatar());

            noti.setType("GROUP_POST");
            noti.setTitle("Bài viết mới");
            noti.setContent(author.getFullName() + " vừa đăng bài trong nhóm");

            noti.setRelatedId(post.getId());
            noti.setRelatedType("POST");

            noti.setCreatedAt(LocalDateTime.now());
            noti.setRead(false);

            notificationService.createNotification(noti);
        }
    }

    // ================= UPDATE =================

    public PostDTO updatePost(String id, PostDTO dto) {

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        post.setContent(dto.getContent());
        post.setImages(dto.getImages());
        post.setVideos(dto.getVideos());
        post.setLocation(dto.getLocation());
        post.setFeeling(dto.getFeeling());
        post.setActivity(dto.getActivity());
        if (dto.getVisibility() != null) {
            post.setVisibility(normalizeVisibility(dto.getVisibility()));
        }

        post.setUpdatedAt(LocalDateTime.now());

        return toDTO(postRepository.save(post));
    }

    // ================= DELETE =================

    public void deletePost(String id) {

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        post.setDeleted(true);
        post.setDeletedAt(LocalDateTime.now());

        postRepository.save(post);
    }

    // ================= MAPPER =================

    private PostDTO toDTO(Post post) {

        PostDTO dto = new PostDTO();

        dto.setId(post.getId());

        if (post.getAuthor() != null) {
            dto.setAuthorId(post.getAuthor().getId());
            dto.setAuthorName(post.getAuthor().getFullName());
            dto.setAuthorAvatar(post.getAuthor().getAvatar());
        }

        dto.setContent(post.getContent());
        dto.setImages(post.getImages());
        dto.setVideos(post.getVideos());
        dto.setLocation(post.getLocation());
        dto.setFeeling(post.getFeeling());
        dto.setActivity(post.getActivity());

        dto.setVisibility(normalizeVisibility(post.getVisibility()));
        dto.setAllowComments(post.getAllowComments());
        dto.setAllowSharing(post.getAllowSharing());

        dto.setLikeCount(post.getLikeCount());
        dto.setCommentCount(post.getCommentCount());
        dto.setShareCount(post.getShareCount());

        dto.setGroupId(post.getGroupId());

        dto.setCreatedAt(post.getCreatedAt());
        dto.setUpdatedAt(post.getUpdatedAt());

        return dto;
    }

    private PostDTO toDTOForViewer(Post post, String viewerId) {
        PostDTO dto = toDTO(post);

        boolean isPrivate = "PRIVATE".equals(normalizeVisibility(post.getVisibility()));
        String authorId = dto.getAuthorId();
        boolean isOwner = authorId != null && viewerId != null && authorId.equals(viewerId);

        if (isPrivate && !isOwner) {
            dto.setAuthorId(null);
            dto.setAuthorName("Ẩn danh");
            dto.setAuthorAvatar(null);
        }

        return dto;
    }

    private Post toEntity(PostDTO dto) {

        Post post = new Post();

        // 🔥 AUTHOR
        if (dto.getAuthorId() != null) {
            User author = userRepository.findById(dto.getAuthorId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            post.setAuthor(author);
        }

        // 🔥 GROUP
        if (dto.getGroupId() != null) {
            post.setGroupId(dto.getGroupId());

            Group group = new Group();
            group.setId(dto.getGroupId());
            post.setGroup(group);
        }

        post.setContent(dto.getContent());
        post.setImages(dto.getImages());
        post.setVideos(dto.getVideos());
        post.setLocation(dto.getLocation());
        post.setFeeling(dto.getFeeling());
        post.setActivity(dto.getActivity());

        post.setVisibility(normalizeVisibility(dto.getVisibility()));
        post.setAllowComments(dto.getAllowComments() != null ? dto.getAllowComments() : true);
        post.setAllowSharing(dto.getAllowSharing() != null ? dto.getAllowSharing() : true);

        return post;
    }

    private String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return "PUBLIC";
        }

        String normalized = visibility.trim().toUpperCase();
        return switch (normalized) {
            case "PRIVATE", "ONLY_ME" -> "PRIVATE";
            default -> "PUBLIC";
        };
    }
}