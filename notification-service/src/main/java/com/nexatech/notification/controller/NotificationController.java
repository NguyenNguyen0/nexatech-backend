package com.nexatech.notification.controller;

import com.nexatech.notification.dto.response.ApiResponse;
import com.nexatech.notification.dto.response.PageResponse;
import com.nexatech.notification.dto.NotificationResponse;
import com.nexatech.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getNotifications(
            @RequestHeader("X-User-Id") UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
