package edu.iuh.fit.se.authservice.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Hybrid Token Store Service.
 *
 * <h3>Design (Enterprise-grade Hybrid Token Management):</h3>
 * <ul>
 *   <li><b>Access Token → Stateless</b>: NOT stored in Redis. Verified purely by
 *       JWT signature (offline, 0ms latency). This eliminates Redis as a bottleneck
 *       under high request volume.</li>
 *   <li><b>Refresh Token → Stateful</b>: Stored in Redis with TTL. Used infrequently
 *       (only when Access Token expires), so Redis load is minimal.</li>
 *   <li><b>Token Blacklisting</b>: On logout, the Access Token's JTI is added to a
 *       Redis blacklist with TTL = remaining token lifetime. The blacklist is tiny
 *       and auto-cleans via TTL.</li>
 *   <li><b>User-level Blacklisting</b>: When admin locks an account, a user-level
 *       blacklist entry is created. All tokens for that user are immediately rejected.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenStoreService {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${jwt.access-token-expiration:900000}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpirationMs;

    @Value("${app.token-store.enabled:false}")
    private boolean tokenStoreEnabled;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @PostConstruct
    public void checkConnection() {
        if (!tokenStoreEnabled) {
            log.info("Redis token store is DISABLED (app.token-store.enabled=false)");
            return;
        }
        try {
            if (stringRedisTemplate.getConnectionFactory() != null) {
                stringRedisTemplate.getConnectionFactory().getConnection().ping();
                log.info("✅ Connected to Redis token store at {}:{}", redisHost, redisPort);
                log.info("📋 Hybrid Token Management: Access Token = Stateless | Refresh Token = Stateful | Blacklist = Enabled");
            }
        } catch (Exception e) {
            log.warn("⚠️ Redis token store unavailable at {}:{} — {}", redisHost, redisPort, e.getMessage());
        }
    }

    // ─── Key Prefixes ──────────────────────────────────────────────────────────

    private String refreshKey(String token) {
        return "refresh:" + token;
    }

    private String blacklistKey(String jti) {
        return "blacklist:" + jti;
    }

    private String userBlacklistKey(String userId) {
        return "user-blacklist:" + userId;
    }

    // ─── Refresh Token (Stateful) ──────────────────────────────────────────────

    /**
     * Store only the Refresh Token in Redis.
     * Access Token is Stateless and NOT stored.
     */
    public void storeTokens(String accessToken, String refreshToken, String userId) {
        if (!tokenStoreEnabled) {
            return;
        }
        // Access Token is STATELESS — intentionally not stored in Redis.
        // This is the core of the Hybrid model: JWT signature verification is
        // done offline at the Gateway, eliminating Redis as a bottleneck.
        if (refreshToken != null && !refreshToken.isEmpty()) {
            stringRedisTemplate.opsForValue().set(
                    refreshKey(refreshToken),
                    userId,
                    Duration.ofMillis(refreshTokenExpirationMs)
            );
        }
    }

    public String getUserIdForRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty() || !tokenStoreEnabled) {
            return null;
        }
        return stringRedisTemplate.opsForValue().get(refreshKey(refreshToken));
    }

    public void deleteRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty() || !tokenStoreEnabled) {
            return;
        }
        stringRedisTemplate.delete(refreshKey(refreshToken));
    }

    // ─── Token Blacklisting (Individual — by JTI) ─────────────────────────────

    /**
     * Add an Access Token's JTI to the blacklist with TTL = remaining lifetime.
     * Called on user logout. The entry auto-expires when the original token
     * would have expired, keeping the blacklist extremely small.
     *
     * @param jti            The JWT ID (unique identifier) from the Access Token
     * @param remainingTtlMs Remaining time-to-live of the token in milliseconds
     */
    public void blacklistAccessToken(String jti, long remainingTtlMs) {
        if (jti == null || jti.isEmpty() || !tokenStoreEnabled || remainingTtlMs <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                blacklistKey(jti),
                "revoked",
                Duration.ofMillis(remainingTtlMs)
        );
        log.info("🚫 Blacklisted Access Token JTI={} for {}ms", jti, remainingTtlMs);
    }

    /**
     * Check if an Access Token's JTI has been blacklisted (e.g. after logout).
     */
    public boolean isAccessTokenBlacklisted(String jti) {
        if (jti == null || jti.isEmpty() || !tokenStoreEnabled) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey(jti)));
    }

    // ─── User-level Blacklisting (Admin Account Lock) ─────────────────────────

    /**
     * Blacklist ALL Access Tokens for a specific user.
     * Called when Admin locks/deactivates an account.
     * TTL = max Access Token lifetime (ensures coverage of all active tokens).
     *
     * @param userId The user ID to blacklist
     */
    public void blacklistUser(String userId) {
        if (userId == null || userId.isEmpty() || !tokenStoreEnabled) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                userBlacklistKey(userId),
                "locked",
                Duration.ofMillis(accessTokenExpirationMs)
        );
        log.info("🔒 Blacklisted ALL tokens for userId={} for {}ms", userId, accessTokenExpirationMs);
    }

    /**
     * Check if a user has been blacklisted (account locked by admin).
     */
    public boolean isUserBlacklisted(String userId) {
        if (userId == null || userId.isEmpty() || !tokenStoreEnabled) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(userBlacklistKey(userId)));
    }

    /**
     * Remove user-level blacklist (e.g. when admin re-activates account).
     */
    public void removeUserBlacklist(String userId) {
        if (userId == null || userId.isEmpty() || !tokenStoreEnabled) {
            return;
        }
        stringRedisTemplate.delete(userBlacklistKey(userId));
        log.info("🔓 Removed user-level blacklist for userId={}", userId);
    }
}
