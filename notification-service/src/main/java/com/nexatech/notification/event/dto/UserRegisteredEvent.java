package com.nexatech.notification.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Published by identity-service to "user.events" topic when a new user registers.
 * eventType = "REGISTERED"
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserRegisteredEvent extends BaseEvent {

    private String userId;
    private String email;
    private String displayName;
}
