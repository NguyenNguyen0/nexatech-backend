package com.nexatech.identity.controller.internal;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.identity.dto.base.ApiResponse;
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

    @PutMapping("/{userId}")
    public ApiResponse<Void> updateUser(@PathVariable String userId, @RequestBody UserUpdateParam param) {
        userService.updateUser(userId, param);
        return ApiResponse.<Void>builder().build();
    }
}
