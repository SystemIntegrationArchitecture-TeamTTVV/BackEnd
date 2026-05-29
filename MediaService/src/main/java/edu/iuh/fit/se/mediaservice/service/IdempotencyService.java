package edu.iuh.fit.se.mediaservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String IDEMPOTENCY_PREFIX = "idempotency:";

    /**
     * Check if a key was already processed
     */
    public boolean isProcessed(String key) {
        if (key == null || key.isBlank()) return false;
        String redisKey = IDEMPOTENCY_PREFIX + key;
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    /**
     * Retrieve cached response mapped to an idempotency key
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getResponse(String key) {
        if (key == null || key.isBlank()) return null;
        String redisKey = IDEMPOTENCY_PREFIX + key;
        Object val = redisTemplate.opsForValue().get(redisKey);
        if (val == null) {
            return null;
        }
        try {
            if (val instanceof String) {
                return objectMapper.readValue((String) val, Map.class);
            }
            return objectMapper.convertValue(val, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize idempotency response for key: {}", key, e);
            return null;
        }
    }

    /**
     * Cache response associated with an idempotency key (defaults to 24 hours TTL)
     */
    public void saveResponse(String key, Map<String, Object> response, long ttlSeconds) {
        if (key == null || key.isBlank()) return;
        String redisKey = IDEMPOTENCY_PREFIX + key;
        try {
            String jsonStr = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(redisKey, jsonStr, ttlSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize idempotency response for key: {}", key, e);
            // Fallback: save raw map if Jackson serialization fails
            redisTemplate.opsForValue().set(redisKey, response, ttlSeconds, TimeUnit.SECONDS);
        }
    }
}
