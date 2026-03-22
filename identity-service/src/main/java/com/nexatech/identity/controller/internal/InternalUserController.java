package com.nexatech.identity.controller.internal;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.identity.dto.base.ApiResponse;
import com.nexatech.identity.dto.request.UserCreationParam;
import com.nexatech.identity.dto.request.UserUpdateParam;
import com.nexatech.identity.service.UserService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/internal/users")
public class InternalUserController {

    UserService userService;

    /** Service-to-service: create Keycloak user (same contract as {@code POST /users}). */
    @PostMapping
    public ApiResponse<String> createUser(@RequestBody UserCreationParam param) {
        return ApiResponse.<String>builder().result(userService.createUser(param)).build();
    }

    /**
     * {@code true} if the email is not used by another Keycloak user (current user excluded by id).
     * Path avoids collision with {@code /{userId}}.
     */
    @GetMapping("/availability/email")
    public ApiResponse<Boolean> emailAvailability(
            @RequestParam String email, @RequestParam("excludeUserId") String excludeUserId) {
        return ApiResponse.<Boolean>builder()
                .result(userService.isEmailAvailableForUser(email, excludeUserId))
                .build();
    }

    @PutMapping("/{userId}")
    public ApiResponse<Void> updateUser(@PathVariable String userId, @Valid @RequestBody UserUpdateParam param) {
        userService.updateUser(userId, param);
        return ApiResponse.<Void>builder().build();
    }
}
