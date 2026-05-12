package com.nexatech.chat.controller;

import com.nexatech.chat.dto.request.TypingIndicatorRequest;
import com.nexatech.chat.dto.response.TypingIndicatorResponse;
import com.nexatech.chat.service.OnlineStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class TypingController {

    private final SimpMessagingTemplate messagingTemplate;
    private final OnlineStatusService onlineStatusService;

    @MessageMapping("/conversations/{conversationId}/typing")
    public void typingIndicator(
            @DestinationVariable UUID conversationId,
            TypingIndicatorRequest request,
            SimpMessageHeaderAccessor headerAccessor) {
        String userIdHeader = headerAccessor.getFirstNativeHeader("X-User-Id");
        UUID userId = parseUserId(userIdHeader);
        if (userId != null) {
            onlineStatusService.refreshPresence(userId);
        }

        boolean typing = request != null && request.isTyping();

        TypingIndicatorResponse payload = TypingIndicatorResponse.builder()
                .conversationId(conversationId)
                .userId(userId)
                .typing(typing)
                .timestamp(Instant.now())
                .build();

        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/typing", payload);
    }

    private UUID parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
