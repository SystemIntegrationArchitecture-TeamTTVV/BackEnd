package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import edu.iuh.fit.se.commonservice.dto.PostDTO;
import edu.iuh.fit.se.commonservice.model.SavedPost;
import edu.iuh.fit.se.commonservice.repository.PostRepository;
import edu.iuh.fit.se.commonservice.repository.SavedPostRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SavedPostService {

    private final SavedPostRepository savedPostRepository;
    private final PostRepository postRepository;
    private final PostService postService; // To reuse existing DTO mapping logic

    public SavedPost savePost(String userId, String postId) {
        if (!postRepository.existsById(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
        
        if (savedPostRepository.existsByUserIdAndPostId(userId, postId)) {
            return savedPostRepository.findByUserIdAndPostId(userId, postId).get();
        }

        SavedPost savedPost = new SavedPost();
        savedPost.setUserId(userId);
        savedPost.setPostId(postId);
        savedPost.setSavedAt(LocalDateTime.now());
        
        return savedPostRepository.save(savedPost);
    }

    public void unsavePost(String userId, String postId) {
        savedPostRepository.deleteByUserIdAndPostId(userId, postId);
    }

    public List<PostDTO> getSavedPosts(String userId) {
        List<SavedPost> savedPosts = savedPostRepository.findByUserIdOrderBySavedAtDesc(userId);
        
        return savedPosts.stream()
                .map(sp -> {
                    try {
                        return postService.getPostById(sp.getPostId());
                    } catch (Exception e) {
                        return null; // Ignore deleted or not found posts
                    }
                })
                .filter(post -> post != null)
                .collect(Collectors.toList());
    }

    public List<String> getSavedPostIds(String userId) {
        return savedPostRepository.findByUserIdOrderBySavedAtDesc(userId).stream()
                .map(SavedPost::getPostId)
                .collect(Collectors.toList());
    }
}
