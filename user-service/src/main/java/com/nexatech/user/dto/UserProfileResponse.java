package com.nexatech.user.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

import com.nexatech.user.model.constant.UserRole;

@Data
@Builder
public class UserProfileResponse {

    private UUID id;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private UserRole role;
    private boolean active;
}
