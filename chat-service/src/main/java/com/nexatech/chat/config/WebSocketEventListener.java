package com.nexatech.chat.config;

import com.nexatech.chat.service.OnlineStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.UUID;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final OnlineStatusService onlineStatusService;
    private final Map<String, ConnectedUser> connectedUsers = new ConcurrentHashMap<>();

    @Value("${app.assignment-agent-roles:STAFF}")
    private String assignmentAgentRoles;

    @EventListener
    public void handleWebSocketConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String userIdHeader = accessor.getFirstNativeHeader("X-User-Id");
        String roleHeader = accessor.getFirstNativeHeader("X-User-Role");
        UUID userId = parseUserId(userIdHeader);
        if (userId != null) {
            connectedUsers.put(accessor.getSessionId(), new ConnectedUser(userId, roleHeader));
            if (isAssignableAgent(roleHeader)) {
                onlineStatusService.markAgentOnline(userId);
            } else {
                onlineStatusService.markOnline(userId);
            }
        }
        log.info("WebSocket connected: sessionId={}, userId={}, role={}", accessor.getSessionId(), userIdHeader, roleHeader);
    }

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        ConnectedUser connectedUser = connectedUsers.remove(accessor.getSessionId());
        if (connectedUser != null) {
            onlineStatusService.markOffline(connectedUser.userId());
        }
        log.info("WebSocket disconnected: sessionId={}, userId={}", accessor.getSessionId(),
                connectedUser != null ? connectedUser.userId() : null);
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

    private boolean isAssignableAgent(String roleHeader) {
        if (roleHeader == null || roleHeader.isBlank()) {
            return false;
        }
        Set<String> configuredRoles = Arrays.stream(assignmentAgentRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(role -> role.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return configuredRoles.contains(roleHeader.trim().toUpperCase(Locale.ROOT));
    }

    private record ConnectedUser(UUID userId, String role) {
    }
}
