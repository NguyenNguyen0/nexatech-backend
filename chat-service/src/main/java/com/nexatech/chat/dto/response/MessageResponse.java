package com.nexatech.chat.dto.response;

import com.nexatech.chat.model.MessageStatus;
import com.nexatech.chat.model.ProductReference;
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
public class MessageResponse {

    private UUID id;
    private UUID conversationId;
    private UUID senderId;
    private UUID recipientId;
    private String content;
    private ProductReference productReference;
    private MessageStatus status;
    private Instant deliveredAt;
    private Instant seenAt;
    private Instant createdAt;
}
