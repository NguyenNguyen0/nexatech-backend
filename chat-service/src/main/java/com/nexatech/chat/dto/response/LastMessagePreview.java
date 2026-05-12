package com.nexatech.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LastMessagePreview {

    private UUID id;
    private UUID senderId;
    private String preview;
    private Instant sentAt;
}
