package com.nexatech.user.controller.internal;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.request.UserCreationRequest;
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

    @PutMapping("/{userId}/enable")
    public ApiResponse<Void> enableUser(@PathVariable String userId) {
        userService.enableUser(userId);
        return ApiResponse.<Void>builder().build();
    }
}
