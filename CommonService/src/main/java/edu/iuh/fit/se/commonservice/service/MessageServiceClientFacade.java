package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.client.MessageServiceClient;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Wrapper around {@link MessageServiceClient} that adds Resilience4j circuit breaker
 * and centralises fallbacks for when MessegeService is down.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceClientFacade {

    private final MessageServiceClient messageServiceClient;

    @Bulkhead(name = "messageService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "getConversationsByUserIdFallback")
    @RateLimiter(name = "messageService", fallbackMethod = "getConversationsByUserIdFallback")
    @Retry(name = "messageService", fallbackMethod = "getConversationsByUserIdFallback")
    @CircuitBreaker(name = "messageService", fallbackMethod = "getConversationsByUserIdFallback")
    public List<Map<String, Object>> getConversationsByUserId(String userId) {
        return messageServiceClient.getConversationsByUserId(userId);
    }

    @Bulkhead(name = "messageService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "getConversationByIdFallback")
    @RateLimiter(name = "messageService", fallbackMethod = "getConversationByIdFallback")
    @Retry(name = "messageService", fallbackMethod = "getConversationByIdFallback")
    @CircuitBreaker(name = "messageService", fallbackMethod = "getConversationByIdFallback")
    public Map<String, Object> getConversationById(String id) {
        return messageServiceClient.getConversationById(id);
    }

    @Bulkhead(name = "messageService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "getMessagesByConversationIdFallback")
    @RateLimiter(name = "messageService", fallbackMethod = "getMessagesByConversationIdFallback")
    @Retry(name = "messageService", fallbackMethod = "getMessagesByConversationIdFallback")
    @CircuitBreaker(name = "messageService", fallbackMethod = "getMessagesByConversationIdFallback")
    public List<Map<String, Object>> getMessagesByConversationId(String conversationId) {
        return messageServiceClient.getMessagesByConversationId(conversationId);
    }

    @SuppressWarnings("unused")
    private List<Map<String, Object>> getConversationsByUserIdFallback(String userId, Throwable throwable) {
        log.warn("⚠️ [MessageServiceClient] Falling back for getConversationsByUserId({}): {}", userId, throwable.getMessage());
        return Collections.emptyList();
    }

    @SuppressWarnings("unused")
    private Map<String, Object> getConversationByIdFallback(String id, Throwable throwable) {
        log.warn("⚠️ [MessageServiceClient] Falling back for getConversationById({}): {}", id, throwable.getMessage());
        return Collections.emptyMap();
    }

    @SuppressWarnings("unused")
    private List<Map<String, Object>> getMessagesByConversationIdFallback(String conversationId, Throwable throwable) {
        log.warn("⚠️ [MessageServiceClient] Falling back for getMessagesByConversationId({}): {}", conversationId, throwable.getMessage());
        return Collections.emptyList();
    }
}

