package edu.iuh.fit.se.commonservice.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Central place to store and manage access / refresh tokens in Redis.
 * <p>
 * Keys:
 * - access:{token}  -> userId (TTL = access token expiration)
 * - refresh:{token} -> userId (TTL = refresh token expiration)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenStoreService {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${jwt.access-token-expiration:1800000}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpirationMs;

    /**
     * Allows turning Redis token store on/off via configuration.
     * When disabled, all methods become no-op and application still runs without Redis.
     */
    @Value("${app.token-store.enabled:false}")
    private boolean tokenStoreEnabled;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @PostConstruct
    public void checkConnection() {
        if (!tokenStoreEnabled) {
            log.info("⚙️ Redis token store is DISABLED (app.token-store.enabled=false)");
            return;
        }
        try {
            if (stringRedisTemplate.getConnectionFactory() != null) {
                stringRedisTemplate.getConnectionFactory().getConnection().ping();
                log.info("✅ Connected to Redis token store at {}:{}", redisHost, redisPort);
            } else {
                log.warn("⚠️ Redis ConnectionFactory is null. Check Redis auto-configuration.");
            }
        } catch (Exception e) {
            log.error("❌ Failed to connect to Redis token store at {}:{} - {}", redisHost, redisPort, e.getMessage());
        }
    }

    private String accessKey(String token) {
        return "access:" + token;
    }

    private String refreshKey(String token) {
        return "refresh:" + token;
    }

    public void storeTokens(String accessToken, String refreshToken, String userId) {
        if (!tokenStoreEnabled) {
            return;
        }
        if (accessToken != null && !accessToken.isEmpty()) {
            stringRedisTemplate.opsForValue().set(
                    accessKey(accessToken),
                    userId,
                    Duration.ofMillis(accessTokenExpirationMs)
            );
        }

        if (refreshToken != null && !refreshToken.isEmpty()) {
            stringRedisTemplate.opsForValue().set(
                    refreshKey(refreshToken),
                    userId,
                    Duration.ofMillis(refreshTokenExpirationMs)
            );
        }
    }

    public String getUserIdForRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return null;
        }
        if (!tokenStoreEnabled) {
            return null;
        }
        return stringRedisTemplate.opsForValue().get(refreshKey(refreshToken));
    }

    public void deleteRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return;
        }
        if (!tokenStoreEnabled) {
            return;
        }
        stringRedisTemplate.delete(refreshKey(refreshToken));
    }

    public void rotateRefreshToken(String oldRefreshToken, String newRefreshToken, String userId) {
        if (!tokenStoreEnabled) {
            return;
        }
        deleteRefreshToken(oldRefreshToken);
        if (newRefreshToken != null && !newRefreshToken.isEmpty()) {
            stringRedisTemplate.opsForValue().set(
                    refreshKey(newRefreshToken),
                    userId,
                    Duration.ofMillis(refreshTokenExpirationMs)
            );
        }
    }
}


