package com.nexatech.identity.event;

import com.nexatech.identity.event.BaseEvent;
import com.nexatech.identity.enums.UserRole;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserRegisteredEvent extends BaseEvent {

    private UUID userId;
    private String email;
    private String fullName;
    private UserRole role;
}
