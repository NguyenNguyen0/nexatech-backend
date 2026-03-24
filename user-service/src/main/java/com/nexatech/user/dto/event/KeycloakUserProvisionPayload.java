package com.nexatech.user.dto.event;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * JSON shape matches identity {@code UserCreationParam} for Keycloak provisioning.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KeycloakUserProvisionPayload {
    String id;
    String username;
    boolean enabled;
    String email;
    boolean emailVerified;
    String firstName;
    String lastName;
    List<KeycloakCredentialPayload> credentials;
}
