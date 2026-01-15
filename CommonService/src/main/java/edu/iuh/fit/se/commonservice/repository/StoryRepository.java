package edu.iuh.fit.se.commonservice.repository;

import edu.iuh.fit.se.commonservice.model.Story;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StoryRepository extends MongoRepository<Story, String> {
    List<Story> findByAuthorIdAndIsActiveTrueOrderByCreatedAtDesc(String authorId);
    List<Story> findByIsActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(LocalDateTime now);
    List<Story> findByIsActiveTrueOrderByCreatedAtDesc();
    void deleteByExpiresAtBefore(LocalDateTime now);
}

