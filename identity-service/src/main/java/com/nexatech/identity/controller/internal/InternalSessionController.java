package com.nexatech.identity.controller.internal;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.identity.dto.base.ApiResponse;
import com.nexatech.identity.service.SessionService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Admin/internal session management endpoints. Secured at the network/gateway level.
 */
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/internal/sessions")
public class InternalSessionController {

    SessionService sessionService;

    // ── User sessions ─────────────────────────────────────────────────────────

    @GetMapping("/users/{userId}")
    public ApiResponse<List<Map<String, Object>>> getUserSessions(@PathVariable String userId) {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .result(sessionService.getUserSessionsRaw(userId))
                .build();
    }

    @PostMapping("/users/{userId}/logout")
    public ApiResponse<Void> logoutUser(@PathVariable String userId) {
        sessionService.logoutUser(userId);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/users/{userId}/offline-sessions/{clientUuid}")
    public ApiResponse<List<Map<String, Object>>> getUserOfflineSessions(
            @PathVariable String userId, @PathVariable String clientUuid) {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .result(sessionService.getUserOfflineSessions(userId, clientUuid))
                .build();
    }

    // ── Single session ────────────────────────────────────────────────────────

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId,
            @RequestParam(value = "isOffline", required = false) Boolean isOffline) {
        sessionService.deleteSession(sessionId, isOffline);
        return ApiResponse.<Void>builder().build();
    }

    // ── Realm-level logout ────────────────────────────────────────────────────

    @PostMapping("/logout-all")
    public ApiResponse<Map<String, Object>> logoutAll() {
        return ApiResponse.<Map<String, Object>>builder()
                .result(sessionService.logoutAll())
                .build();
    }

    // ── Client sessions ───────────────────────────────────────────────────────

    @GetMapping("/clients/{clientUuid}/user-sessions")
    public ApiResponse<List<Map<String, Object>>> getClientUserSessions(
            @PathVariable String clientUuid,
            @RequestParam(value = "first", required = false) Integer first,
            @RequestParam(value = "max", required = false, defaultValue = "100") Integer max) {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .result(sessionService.getClientUserSessions(clientUuid, first, max))
                .build();
    }

    @GetMapping("/clients/{clientUuid}/session-count")
    public ApiResponse<Map<String, Object>> getClientSessionCount(@PathVariable String clientUuid) {
        return ApiResponse.<Map<String, Object>>builder()
                .result(sessionService.getClientSessionCount(clientUuid))
                .build();
    }

    @GetMapping("/client-session-stats")
    public ApiResponse<List<Map<String, Object>>> getClientSessionStats() {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .result(sessionService.getClientSessionStats())
                .build();
    }

    // ── Client offline sessions ───────────────────────────────────────────────

    @GetMapping("/clients/{clientUuid}/offline-sessions")
    public ApiResponse<List<Map<String, Object>>> getClientOfflineSessions(
            @PathVariable String clientUuid,
            @RequestParam(value = "first", required = false) Integer first,
            @RequestParam(value = "max", required = false, defaultValue = "100") Integer max) {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .result(sessionService.getClientOfflineSessions(clientUuid, first, max))
                .build();
    }

    @GetMapping("/clients/{clientUuid}/offline-session-count")
    public ApiResponse<Map<String, Object>> getClientOfflineSessionCount(@PathVariable String clientUuid) {
        return ApiResponse.<Map<String, Object>>builder()
                .result(sessionService.getClientOfflineSessionCount(clientUuid))
                .build();
    }
}
