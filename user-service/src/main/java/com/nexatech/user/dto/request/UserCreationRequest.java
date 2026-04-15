package com.nexatech.user.dto.request;

import java.time.LocalDate;

import com.nexatech.user.model.constant.Gender;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

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
public class UserCreationRequest {
    String id;

    @NotBlank(message = "EMAIL_REQUIRED")
    @Email(message = "INVALID_EMAIL_FORMAT")
    String email;

    @NotBlank(message = "USERNAME_IS_MISSING")
    String username;
    String firstName;
    String lastName;
    String avatarUrl;
    String phone;
    LocalDate dob;
    Gender gender;
    Boolean onBoarded;
}
