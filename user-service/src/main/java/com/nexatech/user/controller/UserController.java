package com.nexatech.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.keycloak.LoginRequest;
import com.nexatech.user.dto.keycloak.LogoutRequest;
import com.nexatech.user.dto.keycloak.RefreshTokenRequest;
import com.nexatech.user.dto.keycloak.UserTokenExchangeResponse;
import com.nexatech.user.dto.request.UserCreationRequest;
import com.nexatech.user.dto.request.UserUpdatenRequest;
import com.nexatech.user.dto.response.UserResponse;
import com.nexatech.user.service.UserService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    UserService userService;

    @PostMapping
    public ApiResponse<UserResponse> createUser(@RequestBody UserCreationRequest request) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.createUser(request))
                .build();
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> getAllUsers(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ApiResponse.<List<UserResponse>>builder()
                .result(userService.getAllUsers())
                .build();
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> getUserById(@PathVariable String userId) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getUserById(userId))
                .build();
    }

    @PutMapping("/{userId}")
    public ApiResponse<UserResponse> updateUser(@PathVariable String userId, @RequestBody UserUpdatenRequest request) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.updateUser(userId, request))
                .build();
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ApiResponse.<Void>builder()
                .build();
    }

    @PostMapping("/login")
    public ApiResponse<UserTokenExchangeResponse> login(@RequestBody LoginRequest request){
        return ApiResponse.<UserTokenExchangeResponse>builder()
                .result(userService.login(request))
                .build();
    }

    @PostMapping("/refresh-token")
    public ApiResponse<UserTokenExchangeResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        return ApiResponse.<UserTokenExchangeResponse>builder()
                .result(userService.refreshToken(request))
                .build();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody LogoutRequest request) {
        userService.logout(request);
        return ApiResponse.<Void>builder().build();
    }
}
