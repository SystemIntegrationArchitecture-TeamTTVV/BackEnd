package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import edu.iuh.fit.se.commonservice.dto.CommentDTO;
import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.model.Comment;
import edu.iuh.fit.se.commonservice.model.Post;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.CommentRepository;
import edu.iuh.fit.se.commonservice.repository.PostRepository;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final SocketService socketService;
    private final NotificationService notificationService;

    public List<CommentDTO> getCommentsByPostId(String postId) {
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .filter(comment -> comment.getParentComment() == null) // Only root comments
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
        Comment comment = toEntity(commentDTO);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        Comment saved = commentRepository.save(comment);
        
        // Update comment count in post
        Post post = postRepository.findById(commentDTO.getPostId())
                .orElseThrow(() -> new RuntimeException("Post not found"));
        post.setCommentCount(post.getCommentCount() + 1);
        postRepository.save(post);
        
        // Update reply count if it's a reply
        if (commentDTO.getParentCommentId() != null) {
            updateReplyCount(commentDTO.getParentCommentId());
            
            // Notify parent comment author about the reply
            Comment parentComment = commentRepository.findById(commentDTO.getParentCommentId()).orElse(null);
            if (parentComment != null && parentComment.getAuthor() != null) {
                String parentAuthorId = parentComment.getAuthor().getId();
                
                // Only notify if not replying to own comment
                if (!parentAuthorId.equals(commentDTO.getUserId())) {
                    User replier = userRepository.findById(commentDTO.getUserId()).orElse(null);
                    if (replier != null) {
                        NotificationDTO replyNotification = new NotificationDTO();
                        replyNotification.setRecipientId(parentAuthorId);
                        replyNotification.setActorId(commentDTO.getUserId());
                        replyNotification.setActorName(replier.getFullName());
                        replyNotification.setActorAvatar(replier.getAvatar());
                        replyNotification.setType("REPLY_COMMENT");
                        replyNotification.setTitle("Phản hồi mới");
                        replyNotification.setContent(replier.getFullName() + " đã phản hồi bình luận của bạn");
                        replyNotification.setRelatedId(commentDTO.getParentCommentId());
                        replyNotification.setRelatedType("COMMENT");
                        
                        notificationService.createNotification(replyNotification);
                    }
                }
            }
        }
        
        CommentDTO savedDTO = toDTO(saved);
        
        // Send socket event - notify post author
        String postAuthorId = post.getAuthor() != null ? post.getAuthor().getId() : null;
        if (postAuthorId != null && !postAuthorId.equals(commentDTO.getUserId())) {
            // Create notification
            User commenter = userRepository.findById(commentDTO.getUserId()).orElse(null);
            if (commenter != null) {
                NotificationDTO notificationDTO = new NotificationDTO();
                notificationDTO.setRecipientId(postAuthorId);
                notificationDTO.setActorId(commentDTO.getUserId());
                notificationDTO.setActorName(commenter.getFullName());
                notificationDTO.setActorAvatar(commenter.getAvatar());
                notificationDTO.setType("COMMENT_POST");
                notificationDTO.setTitle("New Comment");
                notificationDTO.setContent(commenter.getFullName() + " commented on your post");
                notificationDTO.setRelatedId(commentDTO.getPostId());
                notificationDTO.setRelatedType("POST");
                
                notificationService.createNotification(notificationDTO);
            }
            
            socketService.notifyCommentCreated(
                postAuthorId,
                SocketEventDTO.commentCreated(commentDTO.getUserId(), savedDTO)
            );
        }
        
        return savedDTO;
    }

    public CommentDTO updateComment(String id, CommentDTO commentDTO) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comment not found with id: " + id));
        
        comment.setContent(commentDTO.getContent());
        comment.setImages(commentDTO.getImages());
        comment.setUpdatedAt(LocalDateTime.now());
        
        Comment updated = commentRepository.save(comment);
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
    }

    private CommentDTO toDTO(Comment comment) {
        CommentDTO dto = new CommentDTO();
        dto.setId(comment.getId());
        if (comment.getPost() != null) {
            dto.setPostId(comment.getPost().getId());
        }
        if (comment.getAuthor() != null) {
            dto.setUserId(comment.getAuthor().getId());
            dto.setUserName(comment.getAuthor().getFullName());
            dto.setUserAvatar(comment.getAuthor().getAvatar());
        }
        dto.setContent(comment.getContent());
        dto.setImages(comment.getImages());
        if (comment.getParentComment() != null) {
            dto.setParentCommentId(comment.getParentComment().getId());
        }
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
        }
        if (dto.getUserId() != null) {
            User user = userRepository.findById(dto.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            comment.setAuthor(user);
        }
        comment.setContent(dto.getContent());
        comment.setImages(dto.getImages());
        if (dto.getParentCommentId() != null) {
            Comment parent = commentRepository.findById(dto.getParentCommentId())
                    .orElseThrow(() -> new RuntimeException("Parent comment not found"));
            comment.setParentComment(parent);
        }
        comment.setLikeCount(0);
        comment.setReplyCount(0);
        return comment;
    }

    public void updateReplyCount(String parentCommentId) {
        Comment parent = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> new RuntimeException("Parent comment not found"));
        parent.setReplyCount((int) commentRepository.findByParentCommentIdOrderByCreatedAtAsc(parentCommentId).stream().count());
        commentRepository.save(parent);
    }
}

