package com.nexatech.user.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import com.nexatech.user.dto.base.ApiResponse;

@FeignClient(name = "identity-system", url = "${openfeign.identity-service.url}")
public interface IdentitySystemClient {

    @GetMapping("/internal/system/ping")
    ApiResponse<Map<String, String>> ping();
}
