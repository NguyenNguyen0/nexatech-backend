package com.nexatech.chat.config;

import com.nexatech.chat.service.OnlineStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final OnlineStatusService onlineStatusService;

    @EventListener
    public void handleWebSocketConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String userIdHeader = accessor.getFirstNativeHeader("X-User-Id");
        UUID userId = parseUserId(userIdHeader);
        if (userId != null) {
            onlineStatusService.markOnline(userId);
        }
        log.info("WebSocket connected: sessionId={}, userId={}", accessor.getSessionId(), userIdHeader);
    }

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String userIdHeader = accessor.getFirstNativeHeader("X-User-Id");
        UUID userId = parseUserId(userIdHeader);
        if (userId != null) {
            onlineStatusService.markOffline(userId);
        }
        log.info("WebSocket disconnected: sessionId={}, userId={}", accessor.getSessionId(), userIdHeader);
    }

    private UUID parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
