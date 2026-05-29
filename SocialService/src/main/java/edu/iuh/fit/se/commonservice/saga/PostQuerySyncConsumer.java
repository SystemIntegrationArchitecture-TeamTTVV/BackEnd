package edu.iuh.fit.se.commonservice.saga;

import edu.iuh.fit.se.commonservice.event.PostCreatedEvent;
import edu.iuh.fit.se.commonservice.model.Post;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * CQRS Sync Consumer.
 * Listens to Post Events from Kafka and synchronizes the Query Side (Targeted Cache Eviction).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostQuerySyncConsumer {

    private final CacheManager cacheManager;
    private final FriendRepository friendRepository;
    private final PostRepository postRepository;

    @KafkaListener(topics = "ttvv.post.created", groupId = "commonservice-cqrs-posts")
    public void onPostCreated(PostCreatedEvent event) {
        log.info("[CQRS-Sync] Post created event received for sync. authorId={}. Evicting specific feed caches.", event.authorId());
        evictDetailCache(event.postId());
        if (event.authorId() != null) {
            evictFeedCachesForAuthor(event.authorId());
        }
    }

    @KafkaListener(topics = "ttvv.post.deleted", groupId = "commonservice-cqrs-posts")
    public void onPostDeleted(String postId) {
        log.info("[CQRS-Sync] Post deleted event received for sync. postId={}. Evicting specific caches.", postId);
        evictDetailCache(postId);
        
        postRepository.findById(postId).ifPresent(post -> {
            if (post.getAuthorId() != null) {
                evictFeedCachesForAuthor(post.getAuthorId());
            }
        });
    }

    private void evictDetailCache(String postId) {
        try {
            var detailCache = cacheManager.getCache("post-detail");
            if (detailCache != null && postId != null) {
                detailCache.evict(postId);
                log.info("[CQRS-Sync] Evicted post-detail cache for postId={}", postId);
            }
        } catch (Exception e) {
            log.error("[CQRS-Sync] Failed to evict detail cache: {}", e.getMessage(), e);
        }
    }

    private void evictFeedCachesForAuthor(String authorId) {
        try {
            var feedCache = cacheManager.getCache("posts-feed");
            if (feedCache != null && authorId != null) {
                // Evict for the author themselves
                feedCache.evict(authorId);
                
                // Evict for all their friends
                friendRepository.findByUserIdOrFriendId(authorId, authorId).forEach(f -> {
                    String friendId = authorId.equals(f.getUserId()) ? f.getFriendId() : f.getUserId();
                    feedCache.evict(friendId);
                });
                
                // Also evict the anonymous cache
                feedCache.evict("anonymous");
                
                log.info("[CQRS-Sync] Targeted eviction of posts-feed cache completed for authorId={}", authorId);
            }
        } catch (Exception e) {
            log.error("[CQRS-Sync] Failed to perform targeted feed cache eviction: {}", e.getMessage(), e);
        }
    }
}
