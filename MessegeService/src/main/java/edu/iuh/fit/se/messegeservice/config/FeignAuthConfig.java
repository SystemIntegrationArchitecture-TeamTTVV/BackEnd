package edu.iuh.fit.se.messegeservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign configuration that injects internal service identity headers
 * for service-to-service calls. AuthService and SocialService trust
 * X-Username/X-Role headers (same as API Gateway injects for user requests).
 *
 * This is an internal service account — no real user JWT needed.
 */
@Configuration
public class FeignAuthConfig {

    @Bean
    public RequestInterceptor internalServiceAuthInterceptor() {
        return (RequestTemplate template) -> {
            // Internal service identity — target services trust these headers
            template.header("X-Username", "internal-service");
            template.header("X-Role", "ADMIN");
            template.header("X-User-Id", "system");
        };
    }
}
