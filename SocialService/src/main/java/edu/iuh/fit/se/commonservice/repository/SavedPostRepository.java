package edu.iuh.fit.se.commonservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import edu.iuh.fit.se.commonservice.model.SavedPost;

@Repository
public interface SavedPostRepository extends MongoRepository<SavedPost, String> {
    List<SavedPost> findByUserIdOrderBySavedAtDesc(String userId);
    Optional<SavedPost> findByUserIdAndPostId(String userId, String postId);
    boolean existsByUserIdAndPostId(String userId, String postId);
    void deleteByUserIdAndPostId(String userId, String postId);
}
