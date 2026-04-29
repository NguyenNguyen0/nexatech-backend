package com.nexatech.user.dto.event;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailVerificationEvent {
    String eventId;
    String eventType;
    Instant timestamp;
    String userId;
    String email;
    String displayName;
    String otpCode;
    Long expiresInSeconds;
}
