package com.nexatech.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginRequest {
    /** Login identifier: username, email, or phone (resolved server-side). */
    @NotBlank(message = "USERNAME_IS_MISSING")
    String username;

    @NotBlank(message = "PASSWORD_REQUIRED")
    String password;
}

