package com.nexatech.identity.dto.request;

import jakarta.validation.constraints.Email;
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
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SocialMediaLoginRequest {

    /**
     * Provider type from NextAuth (e.g. {@code GOOGLE}).
     */
    @NotBlank(message = "INVALID_KEY")
    String type;

    /**
     * OAuth account email (SoundCloud-style {@code username} field).
     */
    @NotBlank(message = "EMAIL_REQUIRED")
    @Email(message = "INVALID_EMAIL_FORMAT")
    String username;

    String firstName;

    String lastName;

    /** Google profile picture URL. */
    String avatarUrl;
}
