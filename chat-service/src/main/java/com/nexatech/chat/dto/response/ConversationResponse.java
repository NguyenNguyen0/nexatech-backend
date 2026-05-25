package com.nexatech.chat.dto.response;

import com.nexatech.chat.model.ConversationStatus;
import com.nexatech.chat.model.ConversationType;
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
public class ConversationResponse {

    private UUID id;
    private UUID customerId;
    private UUID agentId;
    private ConversationType conversationType;
    private ConversationStatus status;
    private UUID currentParticipantId;
    private UUID otherUserId;
    private ParticipantResponse participant;
    private boolean otherUserOnline;
    private LastMessagePreview lastMessage;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
}
