package com.nexatech.identity.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import com.nexatech.identity.dto.base.ApiResponse;
import com.nexatech.identity.dto.request.IntrospectTokenRequest;
import com.nexatech.identity.dto.request.LoginRequest;
import com.nexatech.identity.dto.request.ForgotPasswordRequest;
import com.nexatech.identity.dto.request.LogoutRequest;
import com.nexatech.identity.dto.request.RefreshTokenRequest;
import com.nexatech.identity.dto.request.UserCreationParam;
import com.nexatech.identity.dto.response.UserTokenExchangeResponse;
import com.nexatech.identity.service.AuthService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/auth")
public class AuthController {

    AuthService authService;

    @PostMapping("/register")
    public ApiResponse<String> register(@Valid @RequestBody UserCreationParam param) {
        return ApiResponse.<String>builder()
                .result(authService.register(param))
                .build();
    }

    @PostMapping("/login")
    public ApiResponse<UserTokenExchangeResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.<UserTokenExchangeResponse>builder()
                .result(authService.login(request))
                .build();
    }

    @PostMapping("/refresh")
    public ApiResponse<UserTokenExchangeResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.<UserTokenExchangeResponse>builder()
                .result(authService.refreshToken(request))
                .build();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/openid-configuration")
    public ApiResponse<Map<String, Object>> openIdConfiguration() {
        return ApiResponse.<Map<String, Object>>builder()
                .result(authService.getOpenIdConfiguration())
                .build();
    }

    @GetMapping("/userinfo")
    public ApiResponse<Map<String, Object>> userInfo(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.<Map<String, Object>>builder()
                .result(authService.userInfo(authorization))
                .build();
    }

    @PostMapping("/introspect")
    public ApiResponse<Map<String, Object>> introspect(@Valid @RequestBody IntrospectTokenRequest request) {
        return ApiResponse.<Map<String, Object>>builder()
                .result(authService.introspect(request))
                .build();
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @RequestHeader(value = "Authorization") String authorization,
            @Valid @RequestBody com.nexatech.identity.dto.request.PasswordChangeRequest request) {
        authService.changePassword(authorization, request);
        return ApiResponse.<Void>builder().build();
    }

    /**
     * Lightweight session liveness check.
     * Requires a valid, non-revoked Bearer token (goes through SessionRevocationFilter).
     * Returns 200 if the session is still active, 401 if revoked.
     * Used by the frontend heartbeat to detect remote logouts in real time.
     */
    @GetMapping("/session/ping")
    public ApiResponse<Void> sessionPing() {
        return ApiResponse.<Void>builder().build();
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.<Void>builder().build();
    }
}
