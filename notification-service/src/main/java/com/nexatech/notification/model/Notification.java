package com.nexatech.notification.model;

import com.nexatech.notification.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String channel;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    private String failureReason;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "last_retry_at")
    private Instant lastRetryAt;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(name = "email_template_name")
    private String emailTemplateName;

    @Column(name = "email_template_variables", columnDefinition = "TEXT")
    private String emailTemplateVariables;

    public enum NotificationType {
        WELCOME,
        ORDER_CONFIRMATION,
        ORDER_STATUS_UPDATE,
        PAYMENT_CONFIRMATION,
        PAYMENT_FAILED,
        LOW_STOCK_ALERT,
        GENERAL
    }

    public enum NotificationStatus {
        PENDING,
        SENT,
        FAILED,
        READ
    }
}
