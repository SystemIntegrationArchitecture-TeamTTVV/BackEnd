package edu.iuh.fit.se.commonservice.saga;

import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import edu.iuh.fit.se.commonservice.dto.CommentDTO;
import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.event.CommentCreatedEvent;
import edu.iuh.fit.se.commonservice.event.PostLikedEvent;
import edu.iuh.fit.se.commonservice.model.Comment;
import edu.iuh.fit.se.commonservice.model.Post;
import edu.iuh.fit.se.commonservice.model.Video;
import edu.iuh.fit.se.commonservice.repository.CommentRepository;
import edu.iuh.fit.se.commonservice.repository.PostRepository;
import edu.iuh.fit.se.commonservice.repository.ReactionRepository;
import edu.iuh.fit.se.commonservice.repository.VideoRepository;
import edu.iuh.fit.se.commonservice.service.AIViolationCheckService;
import edu.iuh.fit.se.commonservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Engagement Background Consumer.
 * Handles high-concurrency engagement writes (Likes, Comments) asynchronously via Kafka.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngagementConsumer {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReactionRepository reactionRepository;
    private final VideoRepository videoRepository;
    private final AIViolationCheckService aiViolationCheckService;
    private final AuthServiceClient authServiceClient;
    private final NotificationService notificationService;
    private final org.springframework.cache.CacheManager cacheManager;

    @KafkaListener(topics = "ttvv.post.liked", groupId = "commonservice-engagement")
    public void onPostLiked(PostLikedEvent event) {
        log.info("[Engagement-Consumer] Post liked event received. postId={}, userId={}, liked={}",
                event.postId(), event.userId(), event.liked());
        try {
            if (event.postId() != null) {
                postRepository.findById(event.postId()).ifPresent(post -> {
                    long count = reactionRepository.countByPostId(event.postId());
                    post.setLikeCount((int) count);
                    postRepository.save(post);
                    log.info("[Engagement-Consumer] Updated post like count to {} for postId={}", count, event.postId());
                });
            }
        } catch (Exception e) {
            log.error("[Engagement-Consumer] Failed to update post like count: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "ttvv.comment.created", groupId = "commonservice-engagement")
    public void onCommentCreated(CommentCreatedEvent event) {
        log.info("[Engagement-Consumer] Comment created event received for async processing. postId={}, authorId={}",
                event.postId(), event.authorId());
        try {
            // 1. Perform background AI check to make sure comment is safe
            try {
                aiViolationCheckService.checkOrThrow(event.content(), "COMMENT");
            } catch (Exception violationEx) {
                log.warn("[Engagement-Consumer] Blocked toxic comment in background: {}", violationEx.getMessage());
                return; // Toxic comment skipped
            }

            // 2. Resolve commenter name/avatar denormalized
            String authorName = "User";
            String authorAvatar = "";
            try {
                UserDTO user = authServiceClient.getUserById(event.authorId());
                if (user != null) {
                    authorName = user.getFullName() != null ? user.getFullName() : user.getUsername();
                    authorAvatar = user.getAvatar();
                }
            } catch (Exception e) {
                log.warn("[Engagement-Consumer] Failed to fetch user info for comment author: {}", e.getMessage());
            }

            // 3. Save the comment to database
            Comment comment = new Comment();
            if (event.id() != null) {
                comment.setId(event.id());
            }
            comment.setAuthorId(event.authorId());
            comment.setAuthorName(authorName);
            comment.setAuthorAvatar(authorAvatar);
            comment.setContent(event.content());
            comment.setCreatedAt(LocalDateTime.now());
            comment.setUpdatedAt(LocalDateTime.now());
            comment.setLikeCount(0);
            comment.setReplyCount(0);

            if (event.postId() != null) {
                postRepository.findById(event.postId()).ifPresent(post -> {
                    comment.setPost(post);
                    commentRepository.save(comment);

                    // Update Post comment count
                    post.setCommentCount(post.getCommentCount() + 1);
                    postRepository.save(post);
                    log.info("[Engagement-Consumer] Saved comment and updated post commentCount for postId={}", event.postId());

                    // Evict post comments cache to sync CQRS read-path
                    try {
                        var cache = cacheManager.getCache("post-comments");
                        if (cache != null) {
                            cache.evict(event.postId());
                            log.info("[Engagement-Consumer] Evicted post-comments cache for postId={}", event.postId());
                        }
                    } catch (Exception e) {
                        log.warn("[Engagement-Consumer] Failed to evict post-comments cache: {}", e.getMessage());
                    }

                    // Send notification to Post author if different
                    if (post.getAuthorId() != null && !post.getAuthorId().equals(event.authorId())) {
                        NotificationDTO notificationDTO = new NotificationDTO();
                        notificationDTO.setRecipientId(post.getAuthorId());
                        notificationDTO.setActorId(event.authorId());
                        notificationDTO.setActorName(comment.getAuthorName());
                        notificationDTO.setActorAvatar(comment.getAuthorAvatar());
                        notificationDTO.setType("COMMENT_POST");
                        notificationDTO.setTitle("Bình luận mới");
                        notificationDTO.setContent(comment.getAuthorName() + " đã bình luận về bài viết của bạn.");
                        notificationDTO.setRelatedId(event.postId());
                        notificationDTO.setRelatedType("POST");
                        notificationDTO.setRead(false);
                        notificationDTO.setCreatedAt(LocalDateTime.now());
                        notificationService.createNotification(notificationDTO);
                    }
                });
            }
        } catch (Exception e) {
            log.error("[Engagement-Consumer] Failed to process comment created event: {}", e.getMessage(), e);
        }
    }
}
