package com.nexatech.user.controller;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.base.PageResponse;
import com.nexatech.user.dto.request.AdminUserCreateRequest;
import com.nexatech.user.dto.request.UserRoleUpdateRequest;
import com.nexatech.user.dto.request.UserUpdatenRequest;
import com.nexatech.user.dto.request.VerifyOtpRequest;
import com.nexatech.user.dto.response.OnboardingStatusResponse;
import com.nexatech.user.dto.response.UserResponse;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;
import com.nexatech.user.service.AdminUserService;
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
    AdminUserService adminUserService;

    @PostMapping
    // @RequiresPermission(PermissionsAccept.User.CREATE)
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody AdminUserCreateRequest request) {
        return ApiResponse.<UserResponse>builder()
                .result(adminUserService.createUserWithKeycloakProvision(request))
                .build();
    }

    @GetMapping("/me/onboarding-status")
    public ApiResponse<OnboardingStatusResponse> getMyOnboardingStatus(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.<OnboardingStatusResponse>builder()
                .result(userService.getOnboardingStatus(currentUserId(jwt)))
                .build();
    }

    @PostMapping("/me/onboarding/request-otp")
    public ApiResponse<Void> requestMyEmailOtp(@AuthenticationPrincipal Jwt jwt) {
        userService.requestEmailVerificationOtp(currentUserId(jwt));
        return ApiResponse.<Void>builder().build();
    }

    @PostMapping("/me/onboarding/verify-otp")
    public ApiResponse<Void> verifyMyEmailOtp(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VerifyOtpRequest request) {
        userService.verifyEmailVerificationOtp(currentUserId(jwt), request.getOtp());
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping
    // @RequiresPermission(PermissionsAccept.User.READ) // enable for production
    public ApiResponse<PageResponse<UserResponse>> getAllUsers(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "phone", required = false) String phone,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "role", required = false) String role) {
        return ApiResponse.<PageResponse<UserResponse>>builder()
                .result(userService.getAllUsers(page, size, email, phone, active, role))
                .build();
    }

    @GetMapping("/{userId}")
    // @RequiresPermission(PermissionsAccept.User.READ)
    public ApiResponse<UserResponse> getUserById(@PathVariable String userId) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getUserById(userId))
                .build();
    }

    @PutMapping("/{userId}")
    // @RequiresPermission(PermissionsAccept.User.UPDATE)
    public ApiResponse<UserResponse> updateUser(@PathVariable String userId, @Valid @RequestBody UserUpdatenRequest request) {
        UserResponse result = userService.updateUser(userId, request);
        if (request != null && request.getRole() != null) {
            adminUserService.updateUserRole(userId, request.getRole());
        }
        return ApiResponse.<UserResponse>builder()
                .result(result)
                .build();
    }

    @PutMapping("/{userId}/role")
    public ApiResponse<Void> updateUserRole(@PathVariable String userId, @Valid @RequestBody UserRoleUpdateRequest request) {
        adminUserService.updateUserRole(userId, request.getRole());
        return ApiResponse.<Void>builder().build();
    }

    @DeleteMapping("/{userId}/role/{roleName}")
    public ApiResponse<Void> removeUserRole(@PathVariable String userId, @PathVariable String roleName) {
        adminUserService.removeUserRole(userId, roleName);
        return ApiResponse.<Void>builder().build();
    }

    @DeleteMapping("/{userId}")
    // @RequiresPermission(PermissionsAccept.User.DELETE)
    public ApiResponse<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ApiResponse.<Void>builder()
                .build();
    }

    @PutMapping("/{userId}/disable")
    public ApiResponse<Void> disableUser(@PathVariable String userId) {
        userService.disableUser(userId);
        return ApiResponse.<Void>builder().build();
    }

    @PutMapping("/{userId}/enable")
    // @RequiresPermission(PermissionsAccept.User.ENABLE)
    public ApiResponse<Void> enableUser(@PathVariable String userId) {
        userService.enableUser(userId);
        return ApiResponse.<Void>builder()
                .build();
    }

    private String currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return jwt.getSubject();
    }
}
