package com.nexatech.user.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.user.constant.Permissions;
import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.request.AdminUserCreateRequest;
import com.nexatech.user.dto.request.UserUpdatenRequest;
import com.nexatech.user.dto.response.UserResponse;
import com.nexatech.user.security.RequiresPermission;
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
    @RequiresPermission(Permissions.User.CREATE)
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody AdminUserCreateRequest request) {
        return ApiResponse.<UserResponse>builder()
                .result(adminUserService.createUserWithKeycloakProvision(request))
                .build();
    }

    @GetMapping
    @RequiresPermission(Permissions.User.READ)
    public ApiResponse<List<UserResponse>> getAllUsers() {
        return ApiResponse.<List<UserResponse>>builder()
                .result(userService.getAllUsers())
                .build();
    }

    @GetMapping("/{userId}")
    @RequiresPermission(Permissions.User.READ)
    public ApiResponse<UserResponse> getUserById(@PathVariable String userId) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getUserById(userId))
                .build();
    }

    @PutMapping("/{userId}")
    @RequiresPermission(Permissions.User.UPDATE)
    public ApiResponse<UserResponse> updateUser(@PathVariable String userId, @Valid @RequestBody UserUpdatenRequest request) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.updateUser(userId, request))
                .build();
    }

    @DeleteMapping("/{userId}")
    @RequiresPermission(Permissions.User.DELETE)
    public ApiResponse<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ApiResponse.<Void>builder()
                .build();
    }

    @PutMapping("/{userId}/enable")
    @RequiresPermission(Permissions.User.ENABLE)
    public ApiResponse<Void> enableUser(@PathVariable String userId) {
        userService.enableUser(userId);
        return ApiResponse.<Void>builder()
                .build();
    }
}
