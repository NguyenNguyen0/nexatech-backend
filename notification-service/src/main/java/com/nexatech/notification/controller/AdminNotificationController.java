package com.nexatech.notification.controller;

import com.nexatech.notification.constant.PermissionsAccept;
import com.nexatech.notification.dto.NotificationResponse;
import com.nexatech.notification.dto.response.ApiResponse;
import com.nexatech.notification.dto.response.PageResponse;
import com.nexatech.notification.security.RequiresPermission;
import com.nexatech.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;

    /**
     * GET /api/v1/admin/notifications/failed — admin view of all failed notifications.
     */
    @GetMapping("/failed")
    @RequiresPermission(PermissionsAccept.Notification.ADMIN_READ)
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getFailedNotifications(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<NotificationResponse> page = PageResponse.of(
                notificationService.getFailedNotifications(pageable));
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
