package com.nexatech.user.controller;

import com.nexatech.user.dto.response.ApiResponse;
import com.nexatech.user.dto.response.PageResponse;
import com.nexatech.user.dto.UpdateProfileRequest;
import com.nexatech.user.dto.UserProfileResponse;
import com.nexatech.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(@RequestHeader("X-User-Id") UUID userId) {
        return ApiResponse.ok(userProfileService.getProfile(userId));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResponse> updateMyProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userProfileService.updateProfile(userId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserProfileResponse> getUserById(@PathVariable UUID id) {
        return ApiResponse.ok(userProfileService.getProfile(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<UserProfileResponse>> getAllUsers(Pageable pageable) {
        var page = userProfileService.getAllUsers(pageable);
        return ApiResponse.ok(PageResponse.of(page));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deactivateUser(@PathVariable UUID id) {
        userProfileService.deactivateUser(id);
        return ApiResponse.ok(null);
    }
}
