package com.nexatech.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PasswordChangeRequest {
    @NotBlank(message = "OLD_PASSWORD_REQUIRED")
    String oldPassword;

    @NotBlank(message = "NEW_PASSWORD_REQUIRED")
    String newPassword;
}
