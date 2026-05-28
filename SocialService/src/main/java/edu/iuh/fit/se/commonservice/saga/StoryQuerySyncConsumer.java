package edu.iuh.fit.se.commonservice.saga;

import edu.iuh.fit.se.commonservice.event.StoryCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * CQRS Story Sync Consumer.
 * Listens to Story Events from Kafka and invalidates the Query-side cache.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoryQuerySyncConsumer {

    private final CacheManager cacheManager;

    @KafkaListener(topics = "ttvv.story.created", groupId = "commonservice-cqrs-stories")
    public void onStoryCreated(StoryCreatedEvent event) {
        log.info("[CQRS-StorySync] Story created event received. Evicting stories cache.");
        evictStoriesCache();
    }

    @KafkaListener(topics = "ttvv.story.deleted", groupId = "commonservice-cqrs-stories")
    public void onStoryDeleted(String storyId) {
        log.info("[CQRS-StorySync] Story deleted event received. storyId={}. Evicting stories cache.", storyId);
        evictStoriesCache();
    }

    private void evictStoriesCache() {
        try {
            var cache = cacheManager.getCache("stories-feed");
            if (cache != null) {
                cache.clear();
                log.info("[CQRS-StorySync] Evicted stories-feed cache");
            }
        } catch (Exception e) {
            log.error("[CQRS-StorySync] Failed to evict stories cache: {}", e.getMessage(), e);
        }
    }
}
