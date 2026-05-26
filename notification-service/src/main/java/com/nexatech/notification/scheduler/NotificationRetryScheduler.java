package com.nexatech.notification.scheduler;

import com.nexatech.notification.event.dto.AuditLogEvent;
import com.nexatech.notification.model.Notification;
import com.nexatech.notification.repository.NotificationRepository;
import com.nexatech.notification.service.EmailService;
import com.nexatech.notification.service.audit.AuditEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private static final int MAX_RETRY_COUNT = 3;

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final AuditEventPublisher auditEventPublisher;

    /**
     * Every 5 minutes, query all FAILED notifications with retryCount < 3 and retry sending.
     * After 3 failures, the notification stays FAILED and a WARN log is emitted for monitoring.
     */
    @Scheduled(fixedDelay = 300_000)
    public void retryFailedNotifications() {
        List<Notification> failedNotifications = notificationRepository
                .findByStatusAndRetryCountLessThan(Notification.NotificationStatus.FAILED, MAX_RETRY_COUNT);

        if (failedNotifications.isEmpty()) {
            return;
        }

        log.info("Retrying {} failed notification(s)...", failedNotifications.size());

        for (Notification notification : failedNotifications) {
            retryNotification(notification);
        }
    }

    private void retryNotification(Notification notification) {
        notification.setRetryCount(notification.getRetryCount() + 1);
        notification.setLastRetryAt(Instant.now());

        try {
            emailService.sendEmailForNotification(notification);
            notification.setStatus(Notification.NotificationStatus.SENT);
            notification.setFailureReason(null);
            notificationRepository.save(notification);
            log.info("Retry successful for notificationId={} (attempt #{})",
                    notification.getId(), notification.getRetryCount());
        } catch (Exception e) {
            notification.setFailureReason(e.getMessage());
            notificationRepository.save(notification);

            if (notification.getRetryCount() >= MAX_RETRY_COUNT) {
                log.warn("Notification {} has exhausted all {} retry attempts. "
                                + "userId={}, type={}, subject='{}', lastError='{}'",
                        notification.getId(),
                        MAX_RETRY_COUNT,
                        notification.getUserId(),
                        notification.getType(),
                        notification.getSubject(),
                        e.getMessage());

                Map<String, Object> meta = new HashMap<>();
                meta.put("recipient", notification.getRecipientEmail());
                meta.put("type", notification.getType() != null ? notification.getType().name() : null);
                meta.put("retryCount", notification.getRetryCount());
                auditEventPublisher.publish(AuditLogEvent.builder()
                        .action("NOTIFICATION_RETRY_EXHAUSTED")
                        .entityType("Notification")
                        .entityId(notification.getId() != null ? notification.getId().toString() : null)
                        .entityLabel(notification.getSubject())
                        .actor("system")
                        .reason(e.getMessage())
                        .message("Notification delivery abandoned after " + MAX_RETRY_COUNT + " attempts")
                        .metadata(meta)
                        .build());
            } else {
                log.info("Retry #{} failed for notificationId={}: {}",
                        notification.getRetryCount(), notification.getId(), e.getMessage());
            }
        }
    }
}
