package edu.iuh.fit.se.commonservice.repository;

import edu.iuh.fit.se.commonservice.model.Post;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends MongoRepository<Post, String> {
    List<Post> findByAuthorIdOrderByCreatedAtDesc(String authorId);
    List<Post> findByGroupIdOrderByCreatedAtDesc(String groupId);
    List<Post> findByPageIdOrderByCreatedAtDesc(String pageId);
    List<Post> findByIsDeletedFalseOrderByCreatedAtDesc();
}

