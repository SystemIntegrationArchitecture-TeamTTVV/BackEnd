package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.client.CommonServiceClient;
import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.dto.UserDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Wrapper around {@link CommonServiceClient} that adds Resilience4j circuit breaker
 * and centralises fallbacks for when CommonService is down.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommonServiceClientFacade {

    private final CommonServiceClient commonServiceClient;

    @CircuitBreaker(name = "commonService", fallbackMethod = "getUserByIdFallback")
    public UserDTO getUserById(String id) {
        return commonServiceClient.getUserById(id);
    }

    @SuppressWarnings("unused")
    private UserDTO getUserByIdFallback(String id, Throwable throwable) {
        log.warn("⚠️ [CommonServiceClient] Falling back for getUserById({}): {}", id, throwable.getMessage());
        UserDTO fallback = new UserDTO();
        fallback.setId(id);
        fallback.setUsername("unknown");
        fallback.setFullName("Unknown User");
        fallback.setAvatar(null);
        return fallback;
    }

    @CircuitBreaker(name = "commonService", fallbackMethod = "emitToUserFallback")
    public void emitToUser(String username, SocketEventDTO event) {
        commonServiceClient.emitToUser(username, event);
    }

    @SuppressWarnings("unused")
    private void emitToUserFallback(String username, SocketEventDTO event, Throwable throwable) {
        log.warn("⚠️ [CommonServiceClient] Failed to emitToUser({}) due to {}. Event type: {}",
                username, throwable.getMessage(), event != null ? event.getType() : "null");
    }

    @CircuitBreaker(name = "commonService", fallbackMethod = "emitToAllFallback")
    public void emitToAll(SocketEventDTO event) {
        commonServiceClient.emitToAll(event);
    }

    @SuppressWarnings("unused")
    private void emitToAllFallback(SocketEventDTO event, Throwable throwable) {
        log.warn("⚠️ [CommonServiceClient] Failed to emitToAll due to {}. Event type: {}",
                throwable.getMessage(), event != null ? event.getType() : "null");
    }

    @CircuitBreaker(name = "commonService", fallbackMethod = "emitToTopicFallback")
    public void emitToTopic(String topic, SocketEventDTO event) {
        commonServiceClient.emitToTopic(topic, event);
    }

    @SuppressWarnings("unused")
    private void emitToTopicFallback(String topic, SocketEventDTO event, Throwable throwable) {
        log.warn("⚠️ [CommonServiceClient] Failed to emitToTopic({}) due to {}. Event type: {}",
                topic, throwable.getMessage(), event != null ? event.getType() : "null");
    }
}

