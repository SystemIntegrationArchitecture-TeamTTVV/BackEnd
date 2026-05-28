package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.ReactionDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.model.Comment;
import edu.iuh.fit.se.commonservice.model.Post;
import edu.iuh.fit.se.commonservice.model.Reaction;
import edu.iuh.fit.se.commonservice.model.Video;
import edu.iuh.fit.se.commonservice.repository.CommentRepository;
import edu.iuh.fit.se.commonservice.repository.PostRepository;
import edu.iuh.fit.se.commonservice.repository.ReactionRepository;
import edu.iuh.fit.se.commonservice.repository.VideoRepository;
import edu.iuh.fit.se.commonservice.event.PostLikedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final UserIdentityService userIdentityService;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final SocketService socketService;
    private final NotificationService notificationService;
    private final VideoRepository videoRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public List<ReactionDTO> getReactionsByPostId(String postId) {
        return reactionRepository.findByPostId(postId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<ReactionDTO> getReactionsByCommentId(String commentId) {
        return reactionRepository.findByCommentId(commentId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<ReactionDTO> getReactionsByUserId(String userId) {
        return reactionRepository.findByUserId(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ReactionDTO getReactionById(String id) {
        return reactionRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Reaction not found with id: " + id));
    }

    public ReactionDTO createReaction(ReactionDTO reactionDTO) {
        Reaction existingReaction = null;
        if (reactionDTO.getPostId() != null) {
            existingReaction = reactionRepository.findByUserIdAndPostId(
                    reactionDTO.getUserId(), reactionDTO.getPostId()).orElse(null);
        } else if (reactionDTO.getCommentId() != null) {
            existingReaction = reactionRepository.findByUserIdAndCommentId(
                    reactionDTO.getUserId(), reactionDTO.getCommentId()).orElse(null);
        } else if (reactionDTO.getVideoId() != null) {
            existingReaction = reactionRepository.findByUserIdAndVideoId(
                    reactionDTO.getUserId(), reactionDTO.getVideoId()).orElse(null);
        }

        if (existingReaction != null) {
            existingReaction.setType(reactionDTO.getType());
            Reaction updated = reactionRepository.save(existingReaction);
            
            // Publish Liked Event asynchronously instead of blocking DB count
            publishLikeEvent(reactionDTO.getPostId(), reactionDTO.getUserId(), true);

            ReactionDTO updatedDTO = toDTO(updated);
            sendReactionEvent(reactionDTO, updatedDTO);
            return updatedDTO;
        }

        Reaction reaction = toEntity(reactionDTO);
        reaction.setCreatedAt(LocalDateTime.now());
        Reaction saved = reactionRepository.save(reaction);
        
        // Publish Liked Event asynchronously instead of blocking DB count
        publishLikeEvent(reactionDTO.getPostId(), reactionDTO.getUserId(), true);

        ReactionDTO savedDTO = toDTO(saved);
        sendReactionEvent(reactionDTO, savedDTO);
        return savedDTO;
    }

    private void publishLikeEvent(String postId, String userId, boolean liked) {
        if (postId == null) return;
        try {
            kafkaTemplate.send("ttvv.post.liked", postId,
                    new PostLikedEvent(postId, userId, liked, java.time.Instant.now()));
            log.info("[Kafka] Published ttvv.post.liked event for postId={}, liked={}", postId, liked);
        } catch (Exception e) {
            log.warn("[Kafka] Failed to publish post liked event: {}", e.getMessage());
        }
    }

    private void sendReactionEvent(ReactionDTO reactionDTO, ReactionDTO savedDTO) {
        String recipientId = null;
        UserDTO actor = userIdentityService.findById(reactionDTO.getUserId()).orElse(null);

        if (reactionDTO.getPostId() != null) {
            Post post = postRepository.findById(reactionDTO.getPostId()).orElse(null);
            if (post != null && post.getAuthorId() != null) {
                recipientId = post.getAuthorId();
                if (!recipientId.equals(reactionDTO.getUserId()) && actor != null) {
                    NotificationDTO notificationDTO = new NotificationDTO();
                    notificationDTO.setRecipientId(recipientId);
                    notificationDTO.setActorId(reactionDTO.getUserId());
                    notificationDTO.setActorName(actor.getFullName());
                    notificationDTO.setActorAvatar(actor.getAvatar());
                    notificationDTO.setType("LIKE_POST");
                    notificationDTO.setTitle("New Like");
                    notificationDTO.setContent(actor.getFullName() + " liked your post");
                    notificationDTO.setRelatedId(reactionDTO.getPostId());
                    notificationDTO.setRelatedType("POST");
                    notificationService.createNotification(notificationDTO);
                }
            }
        } else if (reactionDTO.getCommentId() != null) {
            Comment comment = commentRepository.findById(reactionDTO.getCommentId()).orElse(null);
            if (comment != null && comment.getAuthorId() != null) {
                recipientId = comment.getAuthorId();
                if (!recipientId.equals(reactionDTO.getUserId()) && actor != null) {
                    NotificationDTO notificationDTO = new NotificationDTO();
                    notificationDTO.setRecipientId(recipientId);
                    notificationDTO.setActorId(reactionDTO.getUserId());
                    notificationDTO.setActorName(actor.getFullName());
                    notificationDTO.setActorAvatar(actor.getAvatar());
                    notificationDTO.setType("LIKE_COMMENT");
                    notificationDTO.setTitle("New Like");
                    notificationDTO.setContent(actor.getFullName() + " liked your comment");
                    notificationDTO.setRelatedId(reactionDTO.getCommentId());
                    notificationDTO.setRelatedType("COMMENT");
                    notificationService.createNotification(notificationDTO);
                }
            }
        } else if (reactionDTO.getVideoId() != null) {
            Video video = videoRepository.findById(reactionDTO.getVideoId()).orElse(null);
            if (video != null && video.getAuthorId() != null) {
                recipientId = video.getAuthorId();
                if (!recipientId.equals(reactionDTO.getUserId()) && actor != null) {
                    NotificationDTO notificationDTO = new NotificationDTO();
                    notificationDTO.setRecipientId(recipientId);
                    notificationDTO.setActorId(reactionDTO.getUserId());
                    notificationDTO.setActorName(actor.getFullName());
                    notificationDTO.setActorAvatar(actor.getAvatar());
                    notificationDTO.setType("LIKE_VIDEO");
                    notificationDTO.setTitle("New Like");
                    notificationDTO.setContent(actor.getFullName() + " liked your video");
                    notificationDTO.setRelatedId(reactionDTO.getVideoId());
                    notificationDTO.setRelatedType("VIDEO");
                    notificationService.createNotification(notificationDTO);
                }
            }
        }

        if (recipientId != null && !recipientId.equals(reactionDTO.getUserId())) {
            socketService.notifyReactionAdded(
                    recipientId,
                    SocketEventDTO.reactionAdded(reactionDTO.getUserId(), savedDTO)
            );
        }
    }

    public void deleteReaction(String id) {
        Reaction reaction = reactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reaction not found with id: " + id));
        ReactionDTO dto = toDTO(reaction);
        reactionRepository.deleteById(id);
        publishLikeEvent(dto.getPostId(), dto.getUserId(), false);
    }

    public void deleteReactionByPostIdAndUserId(String postId, String userId) {
        reactionRepository.findByUserIdAndPostId(userId, postId)
                .ifPresent(reaction -> {
                    ReactionDTO dto = toDTO(reaction);
                    reactionRepository.delete(reaction);
                    publishLikeEvent(dto.getPostId(), dto.getUserId(), false);
                });
    }

    public void deleteReactionByCommentIdAndUserId(String commentId, String userId) {
        reactionRepository.findByUserIdAndCommentId(userId, commentId)
                .ifPresent(reaction -> {
                    ReactionDTO dto = toDTO(reaction);
                    reactionRepository.delete(reaction);
                    decreaseReactionCounts(dto);
                });
    }

    private void updateReactionCounts(ReactionDTO dto) {
        if (dto.getPostId() != null) {
            Post post = postRepository.findById(dto.getPostId())
                    .orElseThrow(() -> new RuntimeException("Post not found"));
            post.setLikeCount((int) reactionRepository.countByPostId(dto.getPostId()));
            postRepository.save(post);
        } else if (dto.getCommentId() != null) {
            Comment comment = commentRepository.findById(dto.getCommentId())
                    .orElseThrow(() -> new RuntimeException("Comment not found"));
            comment.setLikeCount((int) reactionRepository.countByCommentId(dto.getCommentId()));
            commentRepository.save(comment);
        } else if (dto.getVideoId() != null) {
            Video video = videoRepository.findById(dto.getVideoId())
                    .orElseThrow(() -> new RuntimeException("Video not found"));
            video.setLikeCount((int) reactionRepository.countByVideoId(dto.getVideoId()));
            videoRepository.save(video);
        }
    }

    private void decreaseReactionCounts(ReactionDTO dto) {
        if (dto.getPostId() != null) {
            Post post = postRepository.findById(dto.getPostId())
                    .orElseThrow(() -> new RuntimeException("Post not found"));
            post.setLikeCount(Math.max(0, (int) reactionRepository.countByPostId(dto.getPostId())));
            postRepository.save(post);
        } else if (dto.getCommentId() != null) {
            Comment comment = commentRepository.findById(dto.getCommentId())
                    .orElseThrow(() -> new RuntimeException("Comment not found"));
            comment.setLikeCount(Math.max(0, (int) reactionRepository.countByCommentId(dto.getCommentId())));
            commentRepository.save(comment);
        } else if (dto.getVideoId() != null) {
            Video video = videoRepository.findById(dto.getVideoId())
                    .orElseThrow(() -> new RuntimeException("Video not found"));
            video.setLikeCount(Math.max(0, (int) reactionRepository.countByVideoId(dto.getVideoId())));
            videoRepository.save(video);
        }
    }

    public List<ReactionDTO> getReactionsByVideoId(String videoId) {
        return reactionRepository.findByVideoId(videoId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public void deleteReactionByVideoIdAndUserId(String videoId, String userId) {
        reactionRepository.findByUserIdAndVideoId(userId, videoId)
                .ifPresent(reaction -> {
                    ReactionDTO dto = toDTO(reaction);
                    reactionRepository.delete(reaction);
                    decreaseReactionCounts(dto);
                });
    }

    private ReactionDTO toDTO(Reaction reaction) {
        ReactionDTO dto = new ReactionDTO();
        dto.setId(reaction.getId());
        dto.setUserId(reaction.getUserId());
        userIdentityService.findById(reaction.getUserId()).ifPresent(u -> {
            dto.setUserName(u.getFullName());
            dto.setUserAvatar(u.getAvatar());
        });
        dto.setType(reaction.getType());
        dto.setPostId(reaction.getPostId());
        dto.setCommentId(reaction.getCommentId());
        dto.setVideoId(reaction.getVideoId());
        dto.setCreatedAt(reaction.getCreatedAt());
        return dto;
    }

    private Reaction toEntity(ReactionDTO dto) {
        userIdentityService.getByIdOrThrow(dto.getUserId());
        Reaction reaction = new Reaction();
        reaction.setUserId(dto.getUserId());
        reaction.setType(dto.getType());
        if (dto.getPostId() != null) {
            Post post = postRepository.findById(dto.getPostId())
                    .orElseThrow(() -> new RuntimeException("Post not found"));
            reaction.setPost(post);
            reaction.setPostId(dto.getPostId());
        }
        if (dto.getCommentId() != null) {
            Comment comment = commentRepository.findById(dto.getCommentId())
                    .orElseThrow(() -> new RuntimeException("Comment not found"));
            reaction.setComment(comment);
            reaction.setCommentId(dto.getCommentId());
        }
        if (dto.getVideoId() != null) {
            Video video = videoRepository.findById(dto.getVideoId())
                    .orElseThrow(() -> new RuntimeException("Video not found"));
            reaction.setVideo(video);
            reaction.setVideoId(dto.getVideoId());
        }
        return reaction;
    }
}
