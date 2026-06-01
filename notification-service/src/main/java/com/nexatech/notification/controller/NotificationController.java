package com.nexatech.notification.controller;

import com.nexatech.notification.constant.PermissionsAccept;
import com.nexatech.notification.dto.NotificationPreferenceRequest;
import com.nexatech.notification.dto.NotificationPreferenceResponse;
import com.nexatech.notification.dto.response.ApiResponse;
import com.nexatech.notification.dto.response.PageResponse;
import com.nexatech.notification.dto.NotificationResponse;
import com.nexatech.notification.security.RequiresPermission;
import com.nexatech.notification.service.NotificationPreferenceService;
import com.nexatech.notification.service.NotificationService;
import com.nexatech.notification.service.SseEmitterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final SseEmitterService sseEmitterService;

    @GetMapping
    @RequiresPermission(PermissionsAccept.Notification.READ)
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getNotifications(
            @RequestHeader("X-User-Id") UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<NotificationResponse> page = PageResponse.of(
                notificationService.getNotifications(userId, pageable));
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    // ===== Unread Count =====

    /**
     * GET /api/v1/notifications/unread-count — get the number of unread notifications.
     */
    @GetMapping("/unread-count")
    @RequiresPermission(PermissionsAccept.Notification.READ)
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @RequestHeader("X-User-Id") UUID userId) {
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("unreadCount", count)));
    }

    // ===== Mark as Read =====

    /**
     * PUT /api/v1/notifications/{id}/read — mark a single notification as read.
     */
    @PutMapping("/{id}/read")
    @RequiresPermission(PermissionsAccept.Notification.WRITE)
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable UUID id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Notification marked as read"));
    }

    /**
     * PATCH /api/v1/notifications/{id}/read — mark a single notification as read (PATCH variant).
     */
    @PatchMapping("/{id}/read")
    @RequiresPermission(PermissionsAccept.Notification.WRITE)
    public ResponseEntity<ApiResponse<Void>> markAsReadPatch(@PathVariable UUID id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Notification marked as read"));
    }

    /**
     * PUT /api/v1/notifications/read-all — mark all notifications as read for a user.
     */
    @PutMapping("/read-all")
    @RequiresPermission(PermissionsAccept.Notification.WRITE)
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @RequestHeader("X-User-Id") UUID userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "All notifications marked as read"));
    }

    /**
     * PATCH /api/v1/notifications/read-all — mark all as read (PATCH variant).
     */
    @PatchMapping("/read-all")
    @RequiresPermission(PermissionsAccept.Notification.WRITE)
    public ResponseEntity<ApiResponse<Void>> markAllAsReadPatch(
            @RequestHeader("X-User-Id") UUID userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "All notifications marked as read"));
    }

    // ===== Notification Preferences =====

    /**
     * GET /api/v1/notifications/preferences — get current user's notification preferences.
     */
    @GetMapping("/preferences")
    @RequiresPermission(PermissionsAccept.Notification.READ)
    public ResponseEntity<ApiResponse<List<NotificationPreferenceResponse>>> getPreferences(
            @RequestHeader("X-User-Id") UUID userId) {
        List<NotificationPreferenceResponse> preferences = preferenceService.getPreferences(userId);
        return ResponseEntity.ok(ApiResponse.ok(preferences));
    }

    /**
     * PUT /api/v1/notifications/preferences — update a notification preference.
     */
    @PutMapping("/preferences")
    @RequiresPermission(PermissionsAccept.Notification.WRITE)
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updatePreference(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody NotificationPreferenceRequest request) {
        NotificationPreferenceResponse updated = preferenceService.updatePreference(
                userId,
                request.getNotificationType(),
                request.getEmailEnabled(),
                request.getPushEnabled());
        return ResponseEntity.ok(ApiResponse.ok(updated, "Preference updated"));
    }

    // ===== SSE Stream =====

    /**
     * GET /api/v1/notifications/stream — SSE endpoint for real-time notifications.
     * Client connects with X-User-Id header and receives push events.
     * Timeout: 5 minutes.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader("X-User-Id") UUID userId) {
        return sseEmitterService.addEmitter(userId);
    }
}
