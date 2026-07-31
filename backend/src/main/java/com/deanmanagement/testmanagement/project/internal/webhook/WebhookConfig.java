package com.deanmanagement.testmanagement.project.internal.webhook;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables async webhook delivery and the retry scheduler, and provides a dedicated executor so a
 * slow endpoint never ties up request threads or the common task pool.
 */
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(WebhookProperties.class)
public class WebhookConfig {

    @Bean(name = "webhookExecutor")
    public Executor webhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("webhook-");
        executor.initialize();
        return executor;
    }
}
