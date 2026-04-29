package com.nexatech.notification.service;

import com.nexatech.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service to push notifications to individual users via WebSocket STOMP.
 * Sends to /user/{userId}/queue/notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Push a notification to a specific user via STOMP.
     * The user subscribes to /user/queue/notifications on the client side.
     */
    public void sendToUser(UUID userId, NotificationResponse notification) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/notifications",
                    notification);
            log.debug("WebSocket notification pushed to userId={}", userId);
        } catch (Exception e) {
            log.debug("Failed to push WebSocket notification to userId={}: {}",
                    userId, e.getMessage());
        }
    }

    /**
     * Push an unread count update to a specific user via STOMP.
     * The user subscribes to /user/queue/unread-count on the client side.
     */
    public void sendUnreadCount(UUID userId, long unreadCount) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/unread-count",
                    unreadCount);
            log.debug("WebSocket unread count pushed to userId={}: {}", userId, unreadCount);
        } catch (Exception e) {
            log.debug("Failed to push WebSocket unread count to userId={}: {}",
                    userId, e.getMessage());
        }
    }
}
