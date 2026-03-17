package com.nexatech.identity.controller;

import com.nexatech.identity.dto.response.ApiResponse;
import com.nexatech.identity.dto.request.ChangePasswordRequest;
import com.nexatech.identity.dto.request.LoginRequest;
import com.nexatech.identity.dto.request.RefreshTokenRequest;
import com.nexatech.identity.dto.request.RegisterRequest;
import com.nexatech.identity.dto.response.AuthResponse;
import com.nexatech.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refreshToken(request));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("X-User-Id") UUID userId) {
        authService.logout(userId);
        return ApiResponse.ok(null);
    }
}
