package com.nexatech.user.dto.request;

import java.time.LocalDate;

import com.nexatech.user.model.constant.Gender;

import jakarta.validation.constraints.Email;

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
public class UserUpdatenRequest {
    @Email(message = "INVALID_EMAIL_FORMAT")
    String email;
    String firstName;
    String lastName;
    String avatarUrl;
    String phone;
    LocalDate dob;
    Gender gender;
    Boolean onBoarded;
    /**
     * Optional realm role when admin updates user.
     * - null: keep current role mapping
     * - blank: remove all current realm roles
     * - non-blank: replace current realm roles with this role
     */
    String role;
}
