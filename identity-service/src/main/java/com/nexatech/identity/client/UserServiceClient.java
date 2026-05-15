package com.nexatech.identity.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import com.nexatech.identity.dto.base.ApiResponse;

@FeignClient(name = "user-service", url = "${openfeign.user-service.url}")
public interface UserServiceClient {

    @GetMapping("/internal/system/ping")
    ApiResponse<Map<String, String>> ping();

    @GetMapping("/internal/users/{userId}")
    ApiResponse<Map<String, Object>> getUserById(@org.springframework.web.bind.annotation.PathVariable("userId") String userId);

    @org.springframework.web.bind.annotation.PutMapping("/internal/users/{userId}/requires-password-change")
    ApiResponse<Void> updatePasswordChangeRequired(
            @org.springframework.web.bind.annotation.PathVariable("userId") String userId,
            @org.springframework.web.bind.annotation.RequestBody Boolean required);
}
