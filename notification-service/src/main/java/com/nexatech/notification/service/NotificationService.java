package com.nexatech.notification.service;

import com.nexatech.notification.dto.NotificationResponse;
import com.nexatech.notification.event.dto.AuditLogEvent;
import com.nexatech.notification.model.Notification;
import com.nexatech.notification.repository.NotificationRepository;
import com.nexatech.notification.service.audit.AuditEventPublisher;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseEmitterService sseEmitterService;
    private final WebSocketNotificationService webSocketNotificationService;
    private final AuditEventPublisher auditEventPublisher;

    public Page<NotificationResponse> getNotifications(UUID userId, Pageable pageable) {
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return notificationRepository.findByUserId(userId, sorted)
                .map(this::toResponse);
    }

    /**
     * Get all failed notifications (for admin view).
     */
    public Page<NotificationResponse> getFailedNotifications(Pageable pageable) {
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return notificationRepository.findByStatus(Notification.NotificationStatus.FAILED, sorted)
                .map(this::toResponse);
    }

    /**
     * Get unread notification count for a user.
     * Unread = all notifications whose status is not READ.
     */
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndStatusNot(
                userId, Notification.NotificationStatus.READ);
    }

    public Notification createNotification(UUID userId, Notification.NotificationType type,
                                           String title, String message) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .channel("IN_APP")
                .subject(title)
                .content(message)
                .status(Notification.NotificationStatus.SENT)
                .build();
        notification = notificationRepository.save(notification);

        NotificationResponse response = toResponse(notification);

        // Push real-time event via WebSocket STOMP
        try {
            webSocketNotificationService.sendToUser(userId, response);
            long unreadCount = getUnreadCount(userId);
            webSocketNotificationService.sendUnreadCount(userId, unreadCount);
        } catch (Exception e) {
            log.debug("Failed to push WebSocket event to userId={}: {}", userId, e.getMessage());
        }

        // Push via SSE as fallback
        try {
            sseEmitterService.sendToUser(userId, response);
        } catch (Exception e) {
            log.debug("Failed to push SSE event to userId={}: {}", userId, e.getMessage());
        }

        return notification;
    }

    @Transactional
    public void markAsRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + notificationId));
        notification.setStatus(Notification.NotificationStatus.READ);
        notificationRepository.save(notification);

        // Push updated unread count via WebSocket
        try {
            long unreadCount = getUnreadCount(notification.getUserId());
            webSocketNotificationService.sendUnreadCount(notification.getUserId(), unreadCount);
        } catch (Exception e) {
            log.debug("Failed to push unread count update: {}", e.getMessage());
        }
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.updateStatusByUserId(userId, Notification.NotificationStatus.READ);

        // Push updated unread count via WebSocket
        try {
            webSocketNotificationService.sendUnreadCount(userId, 0L);
        } catch (Exception e) {
            log.debug("Failed to push unread count update: {}", e.getMessage());
        }

        auditEventPublisher.publish(AuditLogEvent.builder()
                .action("NOTIFICATION_MARK_ALL_READ")
                .entityType("Notification")
                .entityId(userId != null ? userId.toString() : null)
                .message("Marked all notifications as read")
                .build());
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .channel(notification.getChannel())
                .subject(notification.getSubject())
                .content(notification.getContent())
                .status(notification.getStatus())
                .failureReason(notification.getFailureReason())
                .retryCount(notification.getRetryCount())
                .lastRetryAt(notification.getLastRetryAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
