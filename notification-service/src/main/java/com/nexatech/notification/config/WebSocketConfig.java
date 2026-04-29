package com.nexatech.notification.config;

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
        // Enable a simple in-memory message broker for destinations prefixed with /topic and /queue
        config.enableSimpleBroker("/topic", "/queue");
        // Prefix for messages from client → server (e.g. @MessageMapping)
        config.setApplicationDestinationPrefixes("/app");
        // Prefix used to target a specific user (e.g. /user/queue/notifications)
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket handshake endpoint
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Raw WebSocket endpoint (without SockJS fallback)
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("*");
    }
}
