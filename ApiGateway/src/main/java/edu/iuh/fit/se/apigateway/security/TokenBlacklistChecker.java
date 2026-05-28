package edu.iuh.fit.se.apigateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Reactive blacklist checker for the API Gateway.
 *
 * <p>Checks two types of blacklist entries in Redis:</p>
 * <ul>
 *   <li><b>Token-level</b> ({@code blacklist:{jti}}): Individual tokens blacklisted on logout</li>
 *   <li><b>User-level</b> ({@code user-blacklist:{userId}}): All tokens for a locked user</li>
 * </ul>
 *
 * <p><b>Fail-open design</b>: If Redis is unavailable, requests are allowed through
 * to prevent a Redis outage from taking down the entire system.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistChecker {

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * Check if a specific Access Token has been blacklisted (by its JTI).
     * Returns {@code true} if the token should be rejected.
     *
     * @param jti    The JWT ID claim from the Access Token
     * @param userId The user ID claim from the Access Token
     * @return Mono<Boolean> — true if blacklisted, false if allowed
     */
    public Mono<Boolean> isBlacklisted(String jti, String userId) {
        if ((jti == null || jti.isEmpty()) && (userId == null || userId.isEmpty())) {
            return Mono.just(false);
        }

        // Check token-level blacklist (logout)
        Mono<Boolean> jtiCheck = (jti != null && !jti.isEmpty())
                ? redisTemplate.hasKey("blacklist:" + jti)
                : Mono.just(false);

        // Check user-level blacklist (admin account lock)
        Mono<Boolean> userCheck = (userId != null && !userId.isEmpty())
                ? redisTemplate.hasKey("user-blacklist:" + userId)
                : Mono.just(false);

        // If EITHER check is true → token is blacklisted
        return Mono.zip(jtiCheck, userCheck)
                .map(tuple -> tuple.getT1() || tuple.getT2())
                .doOnNext(blacklisted -> {
                    if (blacklisted) {
                        log.info("🚫 Token rejected: jti={}, userId={}", jti, userId);
                    }
                })
                .onErrorResume(e -> {
                    // Fail-open: if Redis is down, allow the request through
                    log.warn("⚠️ Redis blacklist check failed (fail-open): {}", e.getMessage());
                    return Mono.just(false);
                });
    }
}
