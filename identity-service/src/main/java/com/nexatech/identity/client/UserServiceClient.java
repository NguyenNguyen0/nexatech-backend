package com.nexatech.identity.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import com.nexatech.identity.dto.base.ApiResponse;

@FeignClient(name = "user-service", url = "${openfeign.user-service.url}")
public interface UserServiceClient {

    @GetMapping("/internal/system/ping")
    ApiResponse<Map<String, String>> ping();
}
