package com.nexatech.user.dto.request;

import java.time.LocalDate;

import com.nexatech.user.model.constant.Gender;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
public class AdminUserCreateRequest {
    String id;

    @NotBlank(message = "EMAIL_REQUIRED")
    @Email(message = "INVALID_EMAIL_FORMAT")
    String email;

    @NotBlank(message = "USERNAME_IS_MISSING")
    @Size(max = 50, message = "USERNAME_TOO_LONG")
    String username;
    String firstName;
    String lastName;
    String avatarUrl;
    String phone;
    LocalDate dob;
    Gender gender;
    Boolean onBoarded;
    /** Password for Keycloak credential (required). */
    @NotBlank(message = "PASSWORD_REQUIRED")
    @Size(min = 8, message = "PASSWORD_TOO_SHORT")
    String password;
    /** Keycloak credential.temporary; null = false (avoid primitive — JSON null breaks Jackson). */
    Boolean temporaryPassword;
    /** Keycloak user enabled; null = true. */
    Boolean enabled;
    /** Keycloak emailVerified; null = false. */
    Boolean emailVerified;
    /**
     * Optional Keycloak realm role to assign after creating user.
     * Example: ADMIN, MANAGER, STAFF, WAREHOUSE, ACCOUNTANT, CUSTOMER
     */
    String role;
}
