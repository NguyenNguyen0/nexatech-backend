package com.nexatech.chat.controller;

import com.nexatech.chat.dto.request.SendMessageRequest;
import com.nexatech.chat.dto.response.ApiResponse;
import com.nexatech.chat.dto.response.ConversationResponse;
import com.nexatech.chat.dto.response.CursorPageResponse;
import com.nexatech.chat.dto.response.MessageResponse;
import com.nexatech.chat.dto.response.PageResponse;
import com.nexatech.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Sort;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public ApiResponse<PageResponse<ConversationResponse>> getConversations(
            @RequestHeader("X-User-Id") UUID userId,
            @PageableDefault(size = 20, sort = "lastMessageAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(chatService.getConversations(userId, pageable)));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<CursorPageResponse<MessageResponse>> getMessages(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID conversationId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(chatService.getMessages(userId, conversationId, cursor, size));
    }

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MessageResponse> sendMessage(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.ok(chatService.sendMessage(userId, conversationId, request));
    }

    @PutMapping("/{conversationId}/messages/{messageId}/delivered")
    public ApiResponse<MessageResponse> markDelivered(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId) {
        return ApiResponse.ok(chatService.markDelivered(userId, conversationId, messageId));
    }

    @PutMapping("/{conversationId}/messages/{messageId}/seen")
    public ApiResponse<MessageResponse> markSeen(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId) {
        return ApiResponse.ok(chatService.markSeen(userId, conversationId, messageId));
    }
}
