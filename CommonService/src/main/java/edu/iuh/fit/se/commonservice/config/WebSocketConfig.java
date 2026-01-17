package edu.iuh.fit.se.commonservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for destinations prefixed with /topic and /queue
        config.enableSimpleBroker("/topic", "/queue", "/user");
        // Set application destination prefix
        config.setApplicationDestinationPrefixes("/app");
        // Set user destination prefix for private messages
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register WebSocket endpoint
        // CORS is handled by API Gateway, so we allow all origins here
        // Gateway will set proper CORS headers
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Allow all origins - Gateway handles CORS
                .withSockJS(); // Enable SockJS fallback options
    }
}

