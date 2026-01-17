package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.ReactionDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.model.Comment;
import edu.iuh.fit.se.commonservice.model.Post;
import edu.iuh.fit.se.commonservice.model.Reaction;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.CommentRepository;
import edu.iuh.fit.se.commonservice.repository.PostRepository;
import edu.iuh.fit.se.commonservice.repository.ReactionRepository;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final SocketService socketService;

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
        // Check if reaction already exists
        Reaction existingReaction = null;
        if (reactionDTO.getPostId() != null) {
            existingReaction = reactionRepository.findByUserIdAndPostId(
                    reactionDTO.getUserId(), reactionDTO.getPostId()).orElse(null);
        } else if (reactionDTO.getCommentId() != null) {
            existingReaction = reactionRepository.findByUserIdAndCommentId(
                    reactionDTO.getUserId(), reactionDTO.getCommentId()).orElse(null);
        }

        if (existingReaction != null) {
            // Update existing reaction
            existingReaction.setType(reactionDTO.getType());
            Reaction updated = reactionRepository.save(existingReaction);
            updateReactionCounts(reactionDTO);
            ReactionDTO updatedDTO = toDTO(updated);
            
            // Send socket event
            sendReactionEvent(reactionDTO, updatedDTO);
            
            return updatedDTO;
        }

        // Create new reaction
        Reaction reaction = toEntity(reactionDTO);
        reaction.setCreatedAt(LocalDateTime.now());
        Reaction saved = reactionRepository.save(reaction);
        updateReactionCounts(reactionDTO);
        ReactionDTO savedDTO = toDTO(saved);
        
        // Send socket event
        sendReactionEvent(reactionDTO, savedDTO);
        
        return savedDTO;
    }
    
    private void sendReactionEvent(ReactionDTO reactionDTO, ReactionDTO savedDTO) {
        String postAuthorId = null;
        if (reactionDTO.getPostId() != null) {
            Post post = postRepository.findById(reactionDTO.getPostId()).orElse(null);
            if (post != null && post.getAuthor() != null) {
                postAuthorId = post.getAuthor().getId();
            }
        }
        
        if (postAuthorId != null && !postAuthorId.equals(reactionDTO.getUserId())) {
            socketService.notifyReactionAdded(
                postAuthorId,
                SocketEventDTO.reactionAdded(reactionDTO.getUserId(), savedDTO)
            );
        }
    }

    public void deleteReaction(String id) {
        Reaction reaction = reactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reaction not found with id: " + id));
        
        ReactionDTO dto = toDTO(reaction);
        reactionRepository.deleteById(id);
        decreaseReactionCounts(dto);
    }

    public void deleteReactionByPostIdAndUserId(String postId, String userId) {
        reactionRepository.findByUserIdAndPostId(userId, postId)
                .ifPresent(reaction -> {
                    ReactionDTO dto = toDTO(reaction);
                    reactionRepository.delete(reaction);
                    decreaseReactionCounts(dto);
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
        }
    }

    private ReactionDTO toDTO(Reaction reaction) {
        ReactionDTO dto = new ReactionDTO();
        dto.setId(reaction.getId());
        dto.setUserId(reaction.getUserId());
        if (reaction.getUser() != null) {
            dto.setUserName(reaction.getUser().getFullName());
            dto.setUserAvatar(reaction.getUser().getAvatar());
        }
        dto.setType(reaction.getType());
        dto.setPostId(reaction.getPostId());
        dto.setCommentId(reaction.getCommentId());
        dto.setCreatedAt(reaction.getCreatedAt());
        return dto;
    }

    private Reaction toEntity(ReactionDTO dto) {
        Reaction reaction = new Reaction();
        if (dto.getUserId() != null) {
            User user = userRepository.findById(dto.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            reaction.setUser(user);
            reaction.setUserId(dto.getUserId());
        }
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
        return reaction;
    }
}

