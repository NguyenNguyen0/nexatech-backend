package com.nexatech.user.controller.internal;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.request.UserCreationRequest;
import com.nexatech.user.dto.request.UserProfileSyncRequest;
import com.nexatech.user.service.UserService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/internal/users")
public class InternalUserController {

    UserService userService;

    @PostMapping
    public ApiResponse<?> createUser(@Valid @RequestBody UserCreationRequest request) {
        return ApiResponse.builder()
                .result(userService.createUser(request))
                .build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/{userId}")
    public ApiResponse<?> getUserById(@PathVariable String userId) {
        return ApiResponse.builder()
                .result(userService.getUserById(userId))
                .build();
    }

    @PutMapping("/{userId}/enable")
    public ApiResponse<Void> enableUser(@PathVariable String userId) {
        userService.enableUser(userId);
        return ApiResponse.<Void>builder().build();
    }

    @PutMapping("/{userId}/profile")
    public ApiResponse<?> syncProfile(
            @PathVariable String userId,
            @RequestBody UserProfileSyncRequest request) {
        return ApiResponse.builder()
                .result(userService.syncProfileFromSocialLogin(userId, request))
                .build();
    }

    @PutMapping("/{userId}/requires-password-change")
    public ApiResponse<Void> updatePasswordChangeRequired(
            @PathVariable String userId,
            @RequestBody Boolean required) {
        userService.updatePasswordChangeRequired(userId, required);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/resolve-login")
    public ApiResponse<java.util.Map<String, String>> resolveLogin(@RequestParam("identifier") String identifier) {
        return ApiResponse.<java.util.Map<String, String>>builder()
                .result(userService.resolveLoginIdentifier(identifier))
                .build();
    }
}
