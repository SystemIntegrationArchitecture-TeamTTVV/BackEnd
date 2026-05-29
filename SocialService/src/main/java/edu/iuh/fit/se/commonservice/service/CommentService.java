package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import edu.iuh.fit.se.commonservice.model.Video;
import edu.iuh.fit.se.commonservice.repository.VideoRepository;
import org.springframework.stereotype.Service;

import edu.iuh.fit.se.commonservice.dto.CommentDTO;
import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.model.Comment;
import edu.iuh.fit.se.commonservice.model.Post;
import edu.iuh.fit.se.commonservice.repository.CommentRepository;
import edu.iuh.fit.se.commonservice.repository.PostRepository;
import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.event.CommentCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final AuthServiceClient authServiceClient;
    private final SocketService socketService;
    private final NotificationService notificationService;
    private final VideoRepository videoRepository;
    private final AIViolationCheckService aiViolationCheckService;
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CacheManager cacheManager;

    public List<CommentDTO> getCommentsByPostId(String postId) {
        org.springframework.data.mongodb.core.query.Criteria criteria = new org.springframework.data.mongodb.core.query.Criteria().orOperator(
                org.springframework.data.mongodb.core.query.Criteria.where("postId").is(postId),
                org.springframework.data.mongodb.core.query.Criteria.where("post.$id").is(org.bson.types.ObjectId.isValid(postId) ? new org.bson.types.ObjectId(postId) : postId)
        );
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query(criteria);
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "createdAt"));
        
        return mongoTemplate.find(query, Comment.class).stream()
                .filter(comment -> comment.getParentComment() == null && comment.getParentCommentId() == null) // Only root comments
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<CommentDTO> getRepliesByParentCommentId(String parentCommentId) {
        return commentRepository.findByParentCommentIdOrderByCreatedAtAsc(parentCommentId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public CommentDTO getCommentById(String id) {
        return commentRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Comment not found with id: " + id));
    }

    public CommentDTO createComment(CommentDTO commentDTO) {
        String preGeneratedId = new org.bson.types.ObjectId().toHexString();
        commentDTO.setId(preGeneratedId);
        commentDTO.setCreatedAt(LocalDateTime.now());
        commentDTO.setLikeCount(0);
        commentDTO.setReplyCount(0);

        if (commentDTO.getUserId() != null) {
            try {
                UserDTO user = authServiceClient.getUserById(commentDTO.getUserId());
                if (user != null) {
                    commentDTO.setUserName(user.getFullName());
                    commentDTO.setUserAvatar(user.getAvatar());
                }
            } catch (Exception e) {
                commentDTO.setUserName("User");
            }
        }

        try {
            kafkaTemplate.send("ttvv.comment.created", preGeneratedId, new CommentCreatedEvent(
                    preGeneratedId,
                    commentDTO.getPostId(),
                    commentDTO.getUserId(),
                    commentDTO.getContent(),
                    commentDTO.getParentCommentId(),
                    java.time.Instant.now()
            ));
            log.info("[Kafka] Published ttvv.comment.created for commentId={}", preGeneratedId);
        } catch (Exception e) {
            log.warn("[Kafka] Failed to publish comment created event. Falling back to synchronous creation: {}", e.getMessage());
            return createCommentSync(commentDTO, preGeneratedId);
        }

        return commentDTO;
    }

    private CommentDTO createCommentSync(CommentDTO commentDTO, String preGeneratedId) {
        Comment comment = toEntity(commentDTO);
        comment.setId(preGeneratedId);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        Comment saved = commentRepository.save(comment);

        if (commentDTO.getPostId() != null) {
            Post post = postRepository.findById(commentDTO.getPostId())
                    .orElseThrow(() -> new RuntimeException("Post not found"));
            post.setCommentCount(post.getCommentCount() + 1);
            postRepository.save(post);
        }

        return toDTO(saved);
    }

    private void notifyVideoAuthor(CommentDTO commentDTO, Video video) {
        String videoAuthorId = video.getAuthorId();
        if (videoAuthorId != null && !videoAuthorId.equals(commentDTO.getUserId())) {
            try {
                UserDTO commenter = authServiceClient.getUserById(commentDTO.getUserId());
                if (commenter != null) {
                    NotificationDTO notificationDTO = new NotificationDTO();
                    notificationDTO.setRecipientId(videoAuthorId);
                    notificationDTO.setActorId(commentDTO.getUserId());
                    notificationDTO.setActorName(commenter.getFullName());
                    notificationDTO.setActorAvatar(commenter.getAvatar());
                    notificationDTO.setType("COMMENT_VIDEO");
                    notificationDTO.setTitle("New Comment");
                    notificationDTO.setContent(commenter.getFullName() + " commented on your video");
                    notificationDTO.setRelatedId(commentDTO.getVideoId());
                    notificationDTO.setRelatedType("VIDEO");
                    
                    notificationService.createNotification(notificationDTO);
                }
            } catch (Exception e) {
                // Ignore if user not found
            }
        }
    }

    public CommentDTO updateComment(String id, CommentDTO commentDTO) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comment not found with id: " + id));
        
        aiViolationCheckService.checkOrThrow(commentDTO.getContent(), "COMMENT");

        comment.setContent(commentDTO.getContent());
        comment.setImages(commentDTO.getImages());
        comment.setUpdatedAt(LocalDateTime.now());
        
        Comment updated = commentRepository.save(comment);
        
        // Evict comment list cache for CQRS sync
        if (comment.getPost() != null) {
            evictCommentsCache(comment.getPost().getId());
        }

        return toDTO(updated);
    }

    public void deleteComment(String id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comment not found with id: " + id));
        
        // Update comment count in post
        Post post = postRepository.findById(comment.getPost().getId())
                .orElseThrow(() -> new RuntimeException("Post not found"));
        post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
        postRepository.save(post);
        
        commentRepository.deleteById(id);

        if (comment.getPost() != null) {
            evictCommentsCache(comment.getPost().getId());
        }
    }

    private void evictCommentsCache(String postId) {
        try {
            var cache = cacheManager.getCache("post-comments");
            if (cache != null && postId != null) {
                cache.evict(postId);
                log.info("[CQRS-Comments] Evicted post-comments cache for postId={}", postId);
            }
        } catch (Exception e) {
            log.warn("[CQRS-Comments] Failed to evict post-comments cache: {}", e.getMessage());
        }
    }

    private CommentDTO toDTO(Comment comment) {
        CommentDTO dto = new CommentDTO();
        dto.setId(comment.getId());
        if (comment.getPost() != null) {
            dto.setPostId(comment.getPost().getId());
        }
        if (comment.getAuthorId() != null) {
            // Fast path: use denormalized fields stored at write time
            if (comment.getAuthorName() != null) {
                dto.setUserId(comment.getAuthorId());
                dto.setUserName(comment.getAuthorName());
                dto.setUserAvatar(comment.getAuthorAvatar());
            } else {
                // Slow path: call AuthService (legacy comments without stored name)
                try {
                    UserDTO user = authServiceClient.getUserById(comment.getAuthorId());
                    dto.setUserId(user.getId());
                    dto.setUserName(user.getFullName());
                    dto.setUserAvatar(user.getAvatar());
                } catch (Exception e) {
                    dto.setUserId(comment.getAuthorId());
                    dto.setUserName(comment.getAuthorId()); // show ID instead of "Unknown User"
                }
            }
        }
        dto.setContent(comment.getContent());
        dto.setImages(comment.getImages());
        if (comment.getParentComment() != null) {
            dto.setParentCommentId(comment.getParentComment().getId());
        } else if (comment.getParentCommentId() != null) {
            dto.setParentCommentId(comment.getParentCommentId());
        }
        dto.setMentionedUserIds(comment.getMentionedUserIds());
        dto.setLikeCount(comment.getLikeCount());
        dto.setReplyCount(comment.getReplyCount());
        dto.setCreatedAt(comment.getCreatedAt());
        dto.setUpdatedAt(comment.getUpdatedAt());
        return dto;
    }

    private Comment toEntity(CommentDTO dto) {
        Comment comment = new Comment();
        if (dto.getPostId() != null) {
            Post post = postRepository.findById(dto.getPostId())
                    .orElseThrow(() -> new RuntimeException("Post not found"));
            comment.setPost(post);
            comment.setPostId(dto.getPostId());
        }
        if (dto.getUserId() != null) {
            comment.setAuthorId(dto.getUserId());
        }
        comment.setContent(dto.getContent());
        comment.setImages(dto.getImages());
        if (dto.getParentCommentId() != null) {
            Comment parent = commentRepository.findById(dto.getParentCommentId())
                    .orElseThrow(() -> new RuntimeException("Parent comment not found"));
            comment.setParentComment(parent);
            comment.setParentCommentId(dto.getParentCommentId());
        }
        if (dto.getVideoId() != null) {  // ✨ THÊM MỚI
            Video video = videoRepository.findById(dto.getVideoId())
                    .orElseThrow(() -> new RuntimeException("Video not found"));
            comment.setVideo(video);
        }
        comment.setMentionedUserIds(dto.getMentionedUserIds());
        comment.setLikeCount(0);
        comment.setReplyCount(0);
        return comment;
    }

    private void notifyMentionedUsersInComment(CommentDTO commentDTO, Comment saved) {
        try {
            UserDTO commenter = authServiceClient.getUserById(commentDTO.getUserId());
            if (commenter == null) return;

            String relatedId = commentDTO.getPostId() != null ? commentDTO.getPostId() : commentDTO.getVideoId();
            String relatedType = commentDTO.getPostId() != null ? "POST" : "VIDEO";

            for (String mentionedUserId : commentDTO.getMentionedUserIds()) {
                if (mentionedUserId == null || mentionedUserId.equals(commentDTO.getUserId())) continue;
                try {
                    NotificationDTO notificationDTO = new NotificationDTO();
                    notificationDTO.setRecipientId(mentionedUserId);
                    notificationDTO.setActorId(commentDTO.getUserId());
                    notificationDTO.setActorName(commenter.getFullName());
                    notificationDTO.setActorAvatar(commenter.getAvatar());
                    notificationDTO.setType("MENTION");
                    notificationDTO.setTitle("Bạn được nhắc đến");
                    notificationDTO.setContent(commenter.getFullName() + " đã nhắc đến bạn trong một bình luận");
                    notificationDTO.setRelatedId(relatedId);
                    notificationDTO.setRelatedType(relatedType);
                    notificationDTO.setRead(false);
                    notificationService.createNotification(notificationDTO);
                } catch (Exception e) {
                    // Skip this mention; don't fail the whole request
                }
            }
        } catch (Exception e) {
            // Ignore if commenter info unavailable
        }
    }

    public void updateReplyCount(String parentCommentId) {
        Comment parent = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> new RuntimeException("Parent comment not found"));
        parent.setReplyCount((int) commentRepository.findByParentCommentIdOrderByCreatedAtAsc(parentCommentId).stream().count());
        commentRepository.save(parent);
    }

    public List<CommentDTO> getCommentsByVideoId(String videoId) {
        return commentRepository.findByVideoIdOrderByCreatedAtAsc(videoId).stream()
                .filter(comment -> comment.getParentComment() == null)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}

