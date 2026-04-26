package edu.iuh.fit.se.commonservice.repository;

import edu.iuh.fit.se.commonservice.model.Comment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends MongoRepository<Comment, String> {
    List<Comment> findByPostIdOrderByCreatedAtAsc(String postId);
    List<Comment> findByParentCommentIdOrderByCreatedAtAsc(String parentCommentId);
    long countByPostId(String postId);
    List<Comment> findByVideoIdOrderByCreatedAtAsc(String videoId);
    long countByVideoId(String videoId);
}

