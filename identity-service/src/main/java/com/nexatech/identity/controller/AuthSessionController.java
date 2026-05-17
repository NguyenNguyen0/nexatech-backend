package com.nexatech.identity.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.identity.dto.base.ApiResponse;
import com.nexatech.identity.dto.response.SessionResponse;
import com.nexatech.identity.service.SessionService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * User-facing session management. All endpoints require a valid Bearer token.
 * Operations are scoped to the authenticated user only (userId from JWT sub claim).
 */
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/auth/sessions")
public class AuthSessionController {

    SessionService sessionService;

    /**
     * List all active sessions of the currently authenticated user.
     * The session matching the current access token's sid claim is marked current=true.
     */
    @GetMapping("/me")
    public ApiResponse<List<SessionResponse>> getMySessions(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        String currentSessionId = jwt.getClaimAsString("sid");
        return ApiResponse.<List<SessionResponse>>builder()
                .result(sessionService.getMySessions(userId, currentSessionId))
                .build();
    }

    /**
     * Sign out a specific session by sessionId.
     * Only allowed when the session belongs to the authenticated user.
     */
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> deleteSession(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String sessionId) {
        String userId = jwt.getSubject();
        verifySessionOwnership(userId, sessionId);
        sessionService.deleteSession(sessionId, null);
        return ApiResponse.<Void>builder().build();
    }

    /**
     * Sign out all active sessions of the authenticated user.
     */
    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        sessionService.logoutUser(userId);
        return ApiResponse.<Void>builder().build();
    }

    /**
     * Verifies that the given sessionId belongs to the given userId before allowing deletion.
     * Throws IllegalArgumentException if the session is not owned by the user.
     */
    private void verifySessionOwnership(String userId, String sessionId) {
        boolean owned = sessionService.getMySessions(userId, null)
                .stream()
                .anyMatch(s -> sessionId.equals(s.getId()));
        if (!owned) {
            throw new IllegalArgumentException("Session not found or does not belong to the current user");
        }
    }
}
