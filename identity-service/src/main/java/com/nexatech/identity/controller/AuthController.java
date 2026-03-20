package com.nexatech.identity.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.identity.dto.base.ApiResponse;
import com.nexatech.identity.dto.request.LoginRequest;
import com.nexatech.identity.dto.request.LogoutRequest;
import com.nexatech.identity.dto.request.RefreshTokenRequest;
import com.nexatech.identity.dto.request.UserCreationParam;
import com.nexatech.identity.dto.request.UserUpdateParam;
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

    @PostMapping("/login")
    public ApiResponse<UserTokenExchangeResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.<UserTokenExchangeResponse>builder()
                .result(authService.login(request))
                .build();
    }

    @PostMapping("/refresh")
    public ApiResponse<UserTokenExchangeResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ApiResponse.<UserTokenExchangeResponse>builder()
                .result(authService.refreshToken(request))
                .build();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.<Void>builder().build();
    }

    @PostMapping("/onboard")
    public ApiResponse<?> onboard(@RequestBody UserCreationParam param) {
        return ApiResponse.<ResponseEntity<?>>builder()
                .result(authService.onboardUser(param))
                .build();
    }

    @PutMapping("/update/{userId}")
    public ApiResponse<?> update(@PathVariable String userId, @RequestBody UserUpdateParam param) {
        return ApiResponse.<ResponseEntity<?>>builder()
                .result(authService.updateUser(userId, param))
                .build();
    }
}
