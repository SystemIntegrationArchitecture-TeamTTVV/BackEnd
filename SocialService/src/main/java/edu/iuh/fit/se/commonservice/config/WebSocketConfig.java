package edu.iuh.fit.se.commonservice.config;

import edu.iuh.fit.se.commonservice.config.socket.SocketDestinations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final RoomSubscriptionAuthInterceptor roomSubscriptionAuthInterceptor;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final StompHandshakeHandler stompHandshakeHandler;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker with dedicated thread pool and heartbeat
        config.enableSimpleBroker(SocketDestinations.BROKER_TOPIC, SocketDestinations.BROKER_QUEUE, SocketDestinations.BROKER_USER)
              .setHeartbeatValue(new long[]{10000, 10000})   // server→client / client→server heartbeat 10s
              .setTaskScheduler(brokerTaskScheduler());       // dedicated scheduler for heartbeat
        // Set application destination prefix
        config.setApplicationDestinationPrefixes(SocketDestinations.APP_PREFIX);
        // Set user destination prefix for private messages
        config.setUserDestinationPrefix(SocketDestinations.USER_PREFIX);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register WebSocket endpoint with auth interceptor and custom handshake handler
        registry.addEndpoint("/ws")
                .setHandshakeHandler(stompHandshakeHandler)
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(roomSubscriptionAuthInterceptor);
        // Thread pool for processing incoming messages from clients
        registration.taskExecutor().corePoolSize(4).maxPoolSize(8);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // Thread pool for sending messages to clients
        registration.taskExecutor().corePoolSize(4).maxPoolSize(8);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(128 * 1024);   // 128KB max incoming message
        registration.setSendBufferSizeLimit(512 * 1024); // 512KB send buffer per session
        registration.setSendTimeLimit(20 * 1000);        // 20s send timeout
    }

    @Bean
    public TaskScheduler brokerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-broker-");
        scheduler.initialize();
        return scheduler;
    }
}
