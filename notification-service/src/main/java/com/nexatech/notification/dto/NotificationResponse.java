package com.nexatech.notification.dto;

import com.nexatech.notification.model.Notification;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {

    private UUID id;
    private Notification.NotificationType type;
    private String channel;
    private String subject;
    private String content;
    private Notification.NotificationStatus status;
    private Instant createdAt;
}
