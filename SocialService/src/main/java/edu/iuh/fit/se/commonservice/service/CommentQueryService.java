package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.CommentDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.model.Comment;
import edu.iuh.fit.se.commonservice.repository.CommentRepository;
import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CQRS - Query Service for Comments.
 * Caches post comments lists using Redis to support massive concurrent reads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentQueryService {

    private final CommentRepository commentRepository;
    private final AuthServiceClient authServiceClient;
    private final MongoTemplate mongoTemplate;

    @Cacheable(value = "post-comments", key = "#postId")
    public List<CommentDTO> getCommentsByPostId(String postId) {
        log.info("[CQRS-CommentsQuery] Cache miss. Fetching root comments from DB for postId={}", postId);
        Criteria criteria = new Criteria().orOperator(
                Criteria.where("postId").is(postId),
                Criteria.where("post.$id").is(org.bson.types.ObjectId.isValid(postId) ? new org.bson.types.ObjectId(postId) : postId)
        );
        Query query = new Query(criteria);
        query.with(Sort.by(Sort.Direction.ASC, "createdAt"));

        return mongoTemplate.find(query, Comment.class).stream()
                .filter(comment -> comment.getParentComment() == null && comment.getParentCommentId() == null)
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

    private CommentDTO toDTO(Comment comment) {
        CommentDTO dto = new CommentDTO();
        dto.setId(comment.getId());
        if (comment.getPost() != null) {
            dto.setPostId(comment.getPost().getId());
        }
        if (comment.getAuthorId() != null) {
            if (comment.getAuthorName() != null) {
                dto.setUserId(comment.getAuthorId());
                dto.setUserName(comment.getAuthorName());
                dto.setUserAvatar(comment.getAuthorAvatar());
            } else {
                try {
                    UserDTO user = authServiceClient.getUserById(comment.getAuthorId());
                    dto.setUserId(user.getId());
                    dto.setUserName(user.getFullName());
                    dto.setUserAvatar(user.getAvatar());
                } catch (Exception e) {
                    dto.setUserId(comment.getAuthorId());
                    dto.setUserName(comment.getAuthorId());
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
        return dto;
    }
}
