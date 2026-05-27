package com.nexatech.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantResponse {
    private UUID id;
    private String type;
    private String displayName;
    private String email;
    private String username;
    private String avatarUrl;
}
