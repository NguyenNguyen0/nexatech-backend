package com.nexatech.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Logs WebSocket STOMP connect/disconnect events for monitoring.
 */
@Slf4j
@Component
public class WebSocketEventListener {

    @EventListener
    public void handleWebSocketConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String userId = accessor.getFirstNativeHeader("X-User-Id");
        log.info("WebSocket connected: sessionId={}, userId={}",
                accessor.getSessionId(), userId);
    }

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("WebSocket disconnected: sessionId={}", accessor.getSessionId());
    }
}
