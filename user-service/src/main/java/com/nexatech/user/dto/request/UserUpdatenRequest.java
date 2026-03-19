package com.nexatech.user.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import com.nexatech.user.model.constant.Gender;

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
    String email;
    String username;
    String firstName;
    String lastName;
    String avatarUrl;
    String phone;
    LocalDate dob;
    Gender gender;
    UUID roleId;
    Boolean onBoarded;
}
