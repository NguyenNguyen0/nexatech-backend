package com.nexatech.identity.dto.request;

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
public class UserClientRequest {
    String id;
    String email;
    String username;
    String firstName;
    String lastName;
    String avatarUrl;
    String phone;
    String dob;
    String gender;
    Boolean onBoarded;
}
