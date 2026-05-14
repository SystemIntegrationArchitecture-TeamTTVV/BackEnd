package edu.iuh.fit.se.commonservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisConnectionLogger {

    private final RedisConnectionFactory redisConnectionFactory;

    @EventListener(ApplicationReadyEvent.class)
    public void checkRedisConnection() {
        log.info("Checking Redis connection...");
        try {
            RedisConnection connection = redisConnectionFactory.getConnection();
            String ping = connection.ping();
            connection.close();
            log.info("✅ Redis Connection Successful! Ping response: {}", ping);
        } catch (Exception e) {
            log.error("❌ Failed to connect to Redis: {}", e.getMessage(), e);
        }
    }
}
