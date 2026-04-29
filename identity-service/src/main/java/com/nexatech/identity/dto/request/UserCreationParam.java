package com.nexatech.identity.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserCreationParam {
    /** When set, Keycloak creates the user with this id (aligns with application user id). */
    String id;

    @NotBlank(message = "USERNAME_IS_MISSING")
    String username;

    boolean enabled;

    @NotBlank(message = "EMAIL_REQUIRED")
    @Email(message = "INVALID_EMAIL_FORMAT")
    String email;

    boolean emailVerified;
    String firstName;
    String lastName;
    String avatarUrl;
    String phone;
    String dob;
    String gender;
    Boolean onBoarded;

    /**
     * Optional realm role name to assign to the created Keycloak user.
     * If omitted/blank, identity-service defaults to `customer`.
     */
    String role;

    @NotEmpty(message = "CREDENTIALS_REQUIRED")
    @Valid
    List<Credential> credentials;
}

