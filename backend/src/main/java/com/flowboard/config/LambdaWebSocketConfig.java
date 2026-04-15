package com.flowboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

/**
 * Lambda-specific WebSocket configuration that provides a no-op
 * {@link SimpMessagingTemplate} so {@link com.flowboard.service.BoardBroadcastService}
 * can be instantiated without the full WebSocket stack.
 *
 * WebSocket real-time updates are not supported in AWS Lambda deployments.
 */
@Configuration
@Profile("lambda")
public class LambdaWebSocketConfig {

    @Bean
    public SimpMessagingTemplate simpMessagingTemplate() {
        ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();
        return new SimpMessagingTemplate(channel) {
            @Override
            public void convertAndSend(String destination, Object payload) {
                // No-op: WebSocket broadcasting is disabled in Lambda
            }
        };
    }
}
