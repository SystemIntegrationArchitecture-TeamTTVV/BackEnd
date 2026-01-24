package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import edu.iuh.fit.se.commonservice.dto.PostDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.model.Post;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.PostRepository;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final SocketService socketService;

    public List<PostDTO> getAllPosts() {
        return postRepository.findByIsDeletedFalseOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public PostDTO getPostById(String id) {
        return postRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
    }

    public List<PostDTO> getPostsByUserId(String userId) {
        return postRepository.findByAuthorIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<PostDTO> getPostsByGroupId(String groupId) {
        return postRepository.findByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<PostDTO> getPostsByPageId(String pageId) {
        return postRepository.findByPageIdOrderByCreatedAtDesc(pageId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public PostDTO createPost(PostDTO postDTO) {
        Post post = toEntity(postDTO);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        post.setDeleted(false);
        Post saved = postRepository.save(post);
        PostDTO savedDTO = toDTO(saved);
        
        // Send socket event
        if (savedDTO.getAuthorId() != null) {
            socketService.notifyPostCreated(
                savedDTO.getAuthorId(),
                SocketEventDTO.postCreated(savedDTO.getAuthorId(), savedDTO)
            );
        }
        
        return savedDTO;
    }

    public PostDTO updatePost(String id, PostDTO postDTO) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
        
        post.setContent(postDTO.getContent());
        post.setImages(postDTO.getImages());
        post.setVideos(postDTO.getVideos());
        post.setLocation(postDTO.getLocation());
        post.setFeeling(postDTO.getFeeling());
        post.setActivity(postDTO.getActivity());
        post.setUpdatedAt(LocalDateTime.now());
        
        Post updated = postRepository.save(post);
        return toDTO(updated);
    }

    public void deletePost(String id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
        post.setDeleted(true);
        post.setDeletedAt(LocalDateTime.now());
        postRepository.save(post);
    }

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
        dto.setVisibility(post.getVisibility());
        dto.setAllowComments(post.getAllowComments());
        dto.setAllowSharing(post.getAllowSharing());
        dto.setLikeCount(post.getLikeCount());
        dto.setCommentCount(post.getCommentCount());
        dto.setShareCount(post.getShareCount());
        if (post.getGroup() != null) {
            dto.setGroupId(post.getGroup().getId());
        }
        if (post.getPage() != null) {
            dto.setPageId(post.getPage().getId());
        }
        dto.setCreatedAt(post.getCreatedAt());
        dto.setUpdatedAt(post.getUpdatedAt());
        return dto;
    }

    private Post toEntity(PostDTO dto) {
        Post post = new Post();
        if (dto.getAuthorId() != null) {
            User author = userRepository.findById(dto.getAuthorId())
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + dto.getAuthorId()));
            post.setAuthor(author);
        }
        post.setContent(dto.getContent());
        post.setImages(dto.getImages());
        post.setVideos(dto.getVideos());
        post.setLocation(dto.getLocation());
        post.setFeeling(dto.getFeeling());
        post.setActivity(dto.getActivity());
        post.setVisibility(dto.getVisibility() != null ? dto.getVisibility() : "PUBLIC");
        post.setAllowComments(dto.getAllowComments() != null ? dto.getAllowComments() : true);
        post.setAllowSharing(dto.getAllowSharing() != null ? dto.getAllowSharing() : true);
        return post;
    }
}

