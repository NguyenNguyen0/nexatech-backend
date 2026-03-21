package com.nexatech.identity.controller;

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
import com.nexatech.identity.dto.request.LogoutRequest;
import com.nexatech.identity.dto.request.RefreshTokenRequest;
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
    public ApiResponse<Map<String, Object>> introspect(@RequestBody IntrospectTokenRequest request) {
        return ApiResponse.<Map<String, Object>>builder()
                .result(authService.introspect(request))
                .build();
    }
}
