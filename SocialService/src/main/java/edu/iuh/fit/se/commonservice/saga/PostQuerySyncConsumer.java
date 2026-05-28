package edu.iuh.fit.se.commonservice.saga;

import edu.iuh.fit.se.commonservice.event.PostCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * CQRS Sync Consumer.
 * Listens to Post Events from Kafka and synchronizes the Query Side (evicts Redis Cache).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostQuerySyncConsumer {

    private final CacheManager cacheManager;

    @KafkaListener(topics = "ttvv.post.created", groupId = "commonservice-cqrs-posts")
    public void onPostCreated(PostCreatedEvent event) {
        log.info("[CQRS-Sync] Post created event received for sync. Evicting feed caches.");
        evictPostCaches(event.postId());
    }

    @KafkaListener(topics = "ttvv.post.deleted", groupId = "commonservice-cqrs-posts")
    public void onPostDeleted(String postId) {
        log.info("[CQRS-Sync] Post deleted event received for sync. postId={}. Evicting caches.", postId);
        evictPostCaches(postId);
    }

    private void evictPostCaches(String postId) {
        try {
            var feedCache = cacheManager.getCache("posts-feed");
            if (feedCache != null) {
                feedCache.clear(); // Evict all feed caches so everyone gets fresh feed
                log.info("[CQRS-Sync] Evicted posts-feed cache");
            }
            var detailCache = cacheManager.getCache("post-detail");
            if (detailCache != null && postId != null) {
                detailCache.evict(postId);
                log.info("[CQRS-Sync] Evicted post-detail cache for postId={}", postId);
            }
        } catch (Exception e) {
            log.error("[CQRS-Sync] Failed to evict post caches: {}", e.getMessage(), e);
        }
    }
}
