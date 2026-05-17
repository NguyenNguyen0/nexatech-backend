package com.nexatech.chat.service;

import com.nexatech.chat.dto.request.SendMessageRequest;
import com.nexatech.chat.dto.response.ConversationResponse;
import com.nexatech.chat.dto.response.CursorPageResponse;
import com.nexatech.chat.dto.response.LastMessagePreview;
import com.nexatech.chat.dto.response.MessageResponse;
import com.nexatech.chat.exception.BadRequestException;
import com.nexatech.chat.exception.ForbiddenException;
import com.nexatech.chat.exception.ResourceNotFoundException;
import com.nexatech.chat.exception.UnauthorizedException;
import com.nexatech.chat.model.ChatConversation;
import com.nexatech.chat.model.ChatMessage;
import com.nexatech.chat.model.ConversationStatus;
import com.nexatech.chat.model.ConversationType;
import com.nexatech.chat.model.MessageStatus;
import com.nexatech.chat.repository.ConversationRepository;
import com.nexatech.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OnlineStatusService onlineStatusService;
    private final ConversationAssignmentService conversationAssignmentService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.last-message-preview-length:120}")
    private int previewLength;

    @Value("${app.guest-conversation-ttl-minutes:1440}")
    private long guestConversationTtlMinutes;

    public Page<ConversationResponse> getConversations(UUID userId, Pageable pageable) {
        requireUserId(userId);
        Page<ChatConversation> page = conversationRepository.findByParticipant(userId, pageable);
        return page.map(conversation -> toConversationResponse(conversation, userId, null));
    }

    @Transactional(readOnly = true)
    public ConversationResponse getCurrentConversation(UUID userId, String guestSessionId) {
        ChatConversation conversation = findCurrentConversation(userId, guestSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Current conversation not found"));
        return toConversationResponse(conversation, userId, guestSessionId);
    }

    @Transactional
    public ConversationResponse createOrGetCurrentConversation(UUID userId, String guestSessionId) {
        ChatConversation conversation = findCurrentConversation(userId, guestSessionId)
                .orElseGet(() -> createConversation(userId, guestSessionId));

        conversationAssignmentService.assignIfPossible(conversation);
        refreshGuestExpiration(conversation);
        ChatConversation saved = conversationRepository.save(conversation);
        return toConversationResponse(saved, userId, guestSessionId);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<MessageResponse> getMessages(
            UUID userId,
            String guestSessionId,
            UUID conversationId,
            UUID cursor,
            Integer size) {
        ChatConversation conversation = getConversationForActor(conversationId, userId, guestSessionId);
        int pageSize = normalizeSize(size);
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<ChatMessage> messages;
        if (cursor == null) {
            messages = messageRepository.findLatest(conversation.getId(), pageable);
        } else {
            ChatMessage cursorMessage = messageRepository.findByIdAndConversationId(cursor, conversation.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + cursor));
            messages = messageRepository.findBefore(
                    conversation.getId(),
                    cursorMessage.getCreatedAt(),
                    cursorMessage.getId(),
                    pageable);
        }

        boolean hasMore = messages.size() > pageSize;
        List<ChatMessage> slice = hasMore ? messages.subList(0, pageSize) : messages;
        UUID nextCursor = hasMore && !slice.isEmpty() ? slice.get(slice.size() - 1).getId() : null;

        List<MessageResponse> content = slice.stream()
                .map(this::toMessageResponse)
                .toList();

        return CursorPageResponse.<MessageResponse>builder()
                .content(content)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    @Transactional
    public MessageResponse sendMessage(
            UUID userId,
            String guestSessionId,
            UUID conversationId,
            SendMessageRequest request) {
        ChatConversation conversation = getConversationForActor(conversationId, userId, guestSessionId);
        conversationAssignmentService.assignIfPossible(conversation);
        UUID senderId = resolveActorId(userId, guestSessionId);
        UUID recipientId = resolveRecipient(conversation, userId, guestSessionId);

        if (recipientId == null && !canSendWhileWaiting(conversation, userId, guestSessionId)) {
            throw new ForbiddenException("User is not part of the conversation");
        }

        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .conversationId(conversation.getId())
                .senderId(senderId)
                .recipientId(recipientId)
                .content(request.getContent())
                .createdAt(Instant.now())
                .build();

        if (recipientId != null && onlineStatusService.isOnline(recipientId)) {
            message.setDeliveredAt(Instant.now());
        }

        ChatMessage saved = messageRepository.save(message);
        refreshGuestExpiration(conversation);
        updateConversationLastMessage(conversation, saved);

        MessageResponse response = toMessageResponse(saved);
        publishMessageUpdate(response);
        return response;
    }

    @Transactional
    public MessageResponse markDelivered(UUID userId, String guestSessionId, UUID conversationId, UUID messageId) {
        ChatMessage message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));

        ensureRecipient(resolveActorId(userId, guestSessionId), message);

        if (message.getDeliveredAt() == null) {
            message.setDeliveredAt(Instant.now());
        }

        MessageResponse response = toMessageResponse(messageRepository.save(message));
        publishMessageUpdate(response);
        return response;
    }

    @Transactional
    public MessageResponse markSeen(UUID userId, String guestSessionId, UUID conversationId, UUID messageId) {
        ChatMessage message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));

        ensureRecipient(resolveActorId(userId, guestSessionId), message);

        if (message.getDeliveredAt() == null) {
            message.setDeliveredAt(Instant.now());
        }
        if (message.getSeenAt() == null) {
            message.setSeenAt(Instant.now());
        }

        MessageResponse response = toMessageResponse(messageRepository.save(message));
        publishMessageUpdate(response);
        return response;
    }

    private void updateConversationLastMessage(ChatConversation conversation, ChatMessage message) {
        conversation.setLastMessageId(message.getId());
        conversation.setLastMessageSenderId(message.getSenderId());
        conversation.setLastMessageAt(message.getCreatedAt());
        conversation.setLastMessagePreview(truncatePreview(message.getContent()));
        conversationRepository.save(conversation);
    }

    private String truncatePreview(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.length() <= previewLength) {
            return trimmed;
        }
        return trimmed.substring(0, previewLength) + "...";
    }

    private ConversationResponse toConversationResponse(
            ChatConversation conversation,
            UUID userId,
            String guestSessionId) {
        UUID otherUserId = resolveOtherUserId(conversation, userId, guestSessionId);
        LastMessagePreview lastMessage = null;
        if (conversation.getLastMessageId() != null) {
            lastMessage = LastMessagePreview.builder()
                    .id(conversation.getLastMessageId())
                    .senderId(conversation.getLastMessageSenderId())
                    .preview(conversation.getLastMessagePreview())
                    .sentAt(conversation.getLastMessageAt())
                    .build();
        }

        return ConversationResponse.builder()
                .id(conversation.getId())
                .customerId(conversation.getCustomerId())
                .agentId(conversation.getAgentId())
                .conversationType(conversation.getConversationType())
                .status(conversation.getStatus())
                .currentParticipantId(resolveActorId(userId, guestSessionId))
                .otherUserId(otherUserId)
                .otherUserOnline(onlineStatusService.isOnline(otherUserId))
                .lastMessage(lastMessage)
                .expiresAt(conversation.getExpiresAt())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private MessageResponse toMessageResponse(ChatMessage message) {
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .recipientId(message.getRecipientId())
                .content(message.getContent())
                .status(resolveStatus(message))
                .deliveredAt(message.getDeliveredAt())
                .seenAt(message.getSeenAt())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private void publishMessageUpdate(MessageResponse response) {
        messagingTemplate.convertAndSend(
                "/topic/conversations/" + response.getConversationId() + "/messages",
                response);
    }

    private MessageStatus resolveStatus(ChatMessage message) {
        if (message.getSeenAt() != null) {
            return MessageStatus.SEEN;
        }
        if (message.getDeliveredAt() != null) {
            return MessageStatus.DELIVERED;
        }
        return MessageStatus.SENT;
    }

    private ChatConversation getConversationForActor(UUID conversationId, UUID userId, String guestSessionId) {
        ChatConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));

        if (!isParticipant(conversation, userId, guestSessionId)) {
            throw new ForbiddenException("User is not part of the conversation");
        }

        return conversation;
    }

    private boolean isParticipant(ChatConversation conversation, UUID userId, String guestSessionId) {
        if (userId != null) {
            return userId.equals(conversation.getCustomerId()) || userId.equals(conversation.getAgentId());
        }
        return isGuestParticipant(conversation, guestSessionId);
    }

    private UUID resolveOtherUserId(ChatConversation conversation, UUID userId, String guestSessionId) {
        if (isGuestParticipant(conversation, guestSessionId)) {
            return conversation.getAgentId();
        }
        if (userId != null && userId.equals(conversation.getCustomerId())) {
            return conversation.getAgentId();
        }
        if (userId != null && userId.equals(conversation.getAgentId())) {
            if (conversation.getConversationType() == ConversationType.GUEST) {
                return guestParticipantId(conversation.getGuestSessionId());
            }
            return conversation.getCustomerId();
        }
        throw new ForbiddenException("User is not part of the conversation");
    }

    private UUID resolveRecipient(ChatConversation conversation, UUID userId, String guestSessionId) {
        if (isGuestParticipant(conversation, guestSessionId)) {
            return conversation.getAgentId();
        }
        if (userId != null && userId.equals(conversation.getCustomerId())) {
            return conversation.getAgentId();
        }
        if (userId != null && userId.equals(conversation.getAgentId())) {
            if (conversation.getConversationType() == ConversationType.GUEST) {
                return guestParticipantId(conversation.getGuestSessionId());
            }
            return conversation.getCustomerId();
        }
        return null;
    }

    private boolean canSendWhileWaiting(ChatConversation conversation, UUID userId, String guestSessionId) {
        if (conversation.getStatus() != ConversationStatus.WAITING) {
            return false;
        }
        return isGuestParticipant(conversation, guestSessionId)
                || (userId != null && userId.equals(conversation.getCustomerId()));
    }

    private java.util.Optional<ChatConversation> findCurrentConversation(UUID userId, String guestSessionId) {
        if (userId != null) {
            return conversationRepository.findCurrentByCustomerId(userId);
        }
        String normalizedGuestSessionId = normalizeGuestSessionId(guestSessionId);
        return conversationRepository.findCurrentByGuestSessionId(normalizedGuestSessionId, Instant.now());
    }

    private ChatConversation createConversation(UUID userId, String guestSessionId) {
        if (userId != null) {
            return ChatConversation.builder()
                    .customerId(userId)
                    .conversationType(ConversationType.AUTHENTICATED)
                    .status(ConversationStatus.WAITING)
                    .build();
        }

        String normalizedGuestSessionId = normalizeGuestSessionId(guestSessionId);
        return ChatConversation.builder()
                .guestSessionId(normalizedGuestSessionId)
                .conversationType(ConversationType.GUEST)
                .status(ConversationStatus.WAITING)
                .expiresAt(nextGuestExpiration())
                .build();
    }

    private void refreshGuestExpiration(ChatConversation conversation) {
        if (conversation.getConversationType() == ConversationType.GUEST) {
            conversation.setExpiresAt(nextGuestExpiration());
        }
    }

    private Instant nextGuestExpiration() {
        return Instant.now().plus(Duration.ofMinutes(guestConversationTtlMinutes));
    }

    private UUID resolveActorId(UUID userId, String guestSessionId) {
        if (userId != null) {
            return userId;
        }
        return guestParticipantId(normalizeGuestSessionId(guestSessionId));
    }

    private boolean isGuestParticipant(ChatConversation conversation, String guestSessionId) {
        if (conversation.getConversationType() != ConversationType.GUEST) {
            return false;
        }
        if (guestSessionId == null || guestSessionId.isBlank()) {
            return false;
        }
        return guestSessionId.trim().equals(conversation.getGuestSessionId());
    }

    private UUID guestParticipantId(String guestSessionId) {
        return UUID.nameUUIDFromBytes(("guest:" + guestSessionId).getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeGuestSessionId(String guestSessionId) {
        if (guestSessionId == null || guestSessionId.isBlank()) {
            throw new UnauthorizedException("Either X-User-Id or X-Guest-Session-Id is required");
        }
        String normalized = guestSessionId.trim();
        if (normalized.length() > 128) {
            throw new BadRequestException("Guest session id is too long");
        }
        return normalized;
    }

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("X-User-Id is required");
        }
    }

    private void ensureRecipient(UUID actorId, ChatMessage message) {
        if (actorId == null || !actorId.equals(message.getRecipientId())) {
            throw new ForbiddenException("Only the recipient can update delivery status");
        }
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
