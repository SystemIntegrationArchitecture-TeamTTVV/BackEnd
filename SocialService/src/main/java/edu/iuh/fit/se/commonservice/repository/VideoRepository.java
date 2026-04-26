package edu.iuh.fit.se.commonservice.repository;

import edu.iuh.fit.se.commonservice.model.Video;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoRepository extends MongoRepository<Video, String> {
    List<Video> findByIsDeletedFalseOrderByCreatedAtDesc();
    List<Video> findByAuthorIdOrderByCreatedAtDesc(String authorId);
    List<Video> findByGroupIdOrderByCreatedAtDesc(String groupId);
    List<Video> findByPageIdOrderByCreatedAtDesc(String pageId);
    List<Video> findByCategoryOrderByCreatedAtDesc(String category);
    List<Video> findByIsDeletedFalseOrderByViewCountDesc(); // Popular videos
}