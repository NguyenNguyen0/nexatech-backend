package com.nexatech.chat.client.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UserProfileResponse {
    private UUID id;
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private String phone;
}
