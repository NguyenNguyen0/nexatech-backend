package com.nexatech.notification.dto;

import com.nexatech.notification.model.Notification;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotificationPreferenceRequest {

    @NotNull(message = "notificationType is required")
    private Notification.NotificationType notificationType;

    @NotNull(message = "emailEnabled is required")
    private Boolean emailEnabled;

    @NotNull(message = "pushEnabled is required")
    private Boolean pushEnabled;
}
