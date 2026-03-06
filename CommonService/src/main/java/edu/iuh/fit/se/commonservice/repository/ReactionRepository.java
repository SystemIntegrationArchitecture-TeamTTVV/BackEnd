package edu.iuh.fit.se.commonservice.repository;

import edu.iuh.fit.se.commonservice.model.Reaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReactionRepository extends MongoRepository<Reaction, String> {
    List<Reaction> findByPostId(String postId);
    List<Reaction> findByCommentId(String commentId);
    List<Reaction> findByUserId(String userId);
    Optional<Reaction> findByUserIdAndPostId(String userId, String postId);
    Optional<Reaction> findByUserIdAndCommentId(String userId, String commentId);
    long countByPostId(String postId);
    long countByCommentId(String commentId);
    void deleteByPostId(String postId);
    void deleteByCommentId(String commentId);
    List<Reaction> findByVideoId(String videoId);
    Optional<Reaction> findByUserIdAndVideoId(String userId, String videoId);
    long countByVideoId(String videoId);
    void deleteByVideoId(String videoId);
}

