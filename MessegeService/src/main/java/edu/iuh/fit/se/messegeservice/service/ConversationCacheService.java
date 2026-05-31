package edu.iuh.fit.se.messegeservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.iuh.fit.se.messegeservice.dto.ConversationDTO;
import edu.iuh.fit.se.messegeservice.model.Conversation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * CQRS Read Model: Redis cache layer for conversation list per user.
 *
 * <p><b>Cache strategy:</b> Cache-Aside with proactive invalidation.
 * <ul>
 *   <li>READ: getConversationsByUserId() checks Redis first (< 5ms), falls back to MongoDB.</li>
 *   <li>WRITE: Any event that changes conversation order/content triggers invalidation via
 *       {@link #invalidateForConversation(Conversation)} or {@link #invalidate(String)}.</li>
 * </ul>
 *
 * <p><b>Key format:</b> {@code conv-list:{userId}} → JSON array of ConversationDTO
 * <br><b>TTL:</b> 5 minutes (safety net in case invalidation misses edge cases)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationCacheService {

    private static final String KEY_PREFIX = "conv-list:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // ── READ ─────────────────────────────────────────────────────────────────

    /**
     * Returns cached conversation list for {@code userId}, or {@code null} on cache miss.
     */
    public List<ConversationDTO> getFromCache(String userId) {
        if (userId == null || userId.isBlank()) return null;
        String key = KEY_PREFIX + userId;
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) return null;
            List<ConversationDTO> result = objectMapper.readValue(json, new TypeReference<>() {});
            log.debug("⚡ [ConvCache] HIT  user={} count={}", userId, result.size());
            return result;
        } catch (Exception e) {
            log.warn("⚠️ [ConvCache] Deserialize failed for user={}, evicting: {}", userId, e.getMessage());
            stringRedisTemplate.delete(key); // corrupt entry → evict
            return null;
        }
    }

    // ── WRITE ────────────────────────────────────────────────────────────────

    /**
     * Stores conversation list for {@code userId} with {@link #CACHE_TTL}.
     */
    public void putToCache(String userId, List<ConversationDTO> conversations) {
        if (userId == null || userId.isBlank() || conversations == null) return;
        String key = KEY_PREFIX + userId;
        try {
            String json = objectMapper.writeValueAsString(conversations);
            stringRedisTemplate.opsForValue().set(key, json, CACHE_TTL);
            log.debug("📦 [ConvCache] PUT   user={} count={}", userId, conversations.size());
        } catch (Exception e) {
            log.warn("⚠️ [ConvCache] Serialize failed for user={}: {}", userId, e.getMessage());
        }
    }

    // ── INVALIDATION ─────────────────────────────────────────────────────────

    /**
     * Evicts cache for a single user.
     */
    public void invalidate(String userId) {
        if (userId == null || userId.isBlank()) return;
        Boolean deleted = stringRedisTemplate.delete(KEY_PREFIX + userId);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("🗑️  [ConvCache] EVICT user={}", userId);
        }
    }

    /**
     * Evicts cache for all participants of a conversation.
     * Call this whenever a conversation's lastMessage, members, or metadata changes.
     */
    public void invalidateForConversation(Conversation conversation) {
        if (conversation == null || conversation.getParticipantIds() == null
                || conversation.getParticipantIds().isEmpty()) {
            return;
        }
        int count = 0;
        for (String participantId : conversation.getParticipantIds()) {
            Boolean deleted = stringRedisTemplate.delete(KEY_PREFIX + participantId);
            if (Boolean.TRUE.equals(deleted)) count++;
        }
        log.info("🗑️  [ConvCache] EVICT conv={} participants={} evicted={}",
                conversation.getId(), conversation.getParticipantIds().size(), count);
    }

    /**
     * Evicts cache for an explicit list of user IDs (e.g. when member list changes).
     */
    public void invalidateForUsers(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        for (String uid : userIds) {
            invalidate(uid);
        }
    }
}
