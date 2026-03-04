package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.client.MessageServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
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

    @CircuitBreaker(name = "messageService", fallbackMethod = "getConversationByIdFallback")
    public Map<String, Object> getConversationById(String id) {
        return messageServiceClient.getConversationById(id);
    }

    @SuppressWarnings("unused")
    private Map<String, Object> getConversationByIdFallback(String id, Throwable throwable) {
        log.warn("⚠️ [MessageServiceClient] Falling back for getConversationById({}): {}", id, throwable.getMessage());
        return Collections.emptyMap();
    }
}

