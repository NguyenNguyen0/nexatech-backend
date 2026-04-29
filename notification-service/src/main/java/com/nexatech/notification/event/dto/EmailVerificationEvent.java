package com.nexatech.notification.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Published by identity-service to "user.events" topic when email verification is requested.
 * eventType = "EMAIL_VERIFICATION_REQUESTED"
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailVerificationEvent extends BaseEvent {

    private String userId;
    private String email;
    private String displayName;
    private String verificationToken;
    private String verificationUrl;
}
