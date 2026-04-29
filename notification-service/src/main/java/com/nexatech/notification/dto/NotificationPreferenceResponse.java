package com.nexatech.notification.dto;

import com.nexatech.notification.model.Notification;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class NotificationPreferenceResponse {

    private UUID id;
    private Notification.NotificationType notificationType;
    private boolean emailEnabled;
    private boolean pushEnabled;
}
