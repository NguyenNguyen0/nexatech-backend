package com.nexatech.chat.model;

import com.nexatech.chat.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "idx_conversations_customer_id", columnList = "customer_id"),
        @Index(name = "idx_conversations_agent_id", columnList = "agent_id"),
        @Index(name = "idx_conversations_guest_session_id", columnList = "guest_session_id"),
        @Index(name = "idx_conversations_last_message_at", columnList = "last_message_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversation extends BaseEntity {

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "agent_id")
    private UUID agentId;

    @Column(name = "guest_session_id", length = 128)
    private String guestSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_type", nullable = false)
    @Builder.Default
    private ConversationType conversationType = ConversationType.AUTHENTICATED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ConversationStatus status = ConversationStatus.WAITING;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_message_id")
    private UUID lastMessageId;

    @Column(name = "last_message_sender_id")
    private UUID lastMessageSenderId;

    @Column(name = "last_message_preview", length = 255)
    private String lastMessagePreview;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;
}
