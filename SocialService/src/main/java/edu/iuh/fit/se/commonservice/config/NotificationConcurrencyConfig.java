package edu.iuh.fit.se.commonservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class NotificationConcurrencyConfig {

    @Bean(name = "wsOutboundExecutor")
    public ThreadPoolTaskExecutor wsOutboundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100000);
        executor.setThreadNamePrefix("ws-out-");
        executor.initialize();
        return executor;
    }
}
