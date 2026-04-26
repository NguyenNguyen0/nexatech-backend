package com.nexatech.user.controller.internal;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.user.dto.base.ApiResponse;

@RestController
@RequestMapping("/internal/system")
public class InternalSystemController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.<Map<String, String>>builder()
                .result(Map.of("status", "UP"))
                .build();
    }
}
