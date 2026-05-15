package com.nexatech.notification.event.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordEvent {
    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String userId;
    private String email;
    private String displayName;
    private String newPassword;
}
