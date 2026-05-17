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
    /** Chỉ dùng khi đổi mật khẩu thường (forgot-password có mật khẩu tạm). */
    String oldPassword;

    @NotBlank(message = "NEW_PASSWORD_REQUIRED")
    String newPassword;

    /**
     * Đặt mật khẩu lần đầu (Google / bắt buộc đổi MK): không xác minh mật khẩu cũ.
     */
    Boolean initialSetup;
}
