package com.nexatech.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "chat_messages")
@CompoundIndex(name = "idx_messages_conversation_created", def = "{'conversationId': 1, 'createdAt': -1, '_id': -1}")
@CompoundIndex(name = "idx_messages_conversation_sender_seen", def = "{'conversationId': 1, 'senderId': 1, 'seenAt': 1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    private UUID id;

    @Indexed
    private UUID conversationId;

    private UUID senderId;

    private UUID recipientId;

    private String content;

    private ProductReference productReference;

    private Instant createdAt;

    private Instant deliveredAt;

    private Instant seenAt;
}
