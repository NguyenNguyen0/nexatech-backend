package com.nexatech.user.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.nexatech.user.client.IdentitySystemClient;
import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IdentityServiceDependencyGuard {

    private final IdentitySystemClient identitySystemClient;

    @Retry(name = "identityService")
    @CircuitBreaker(name = "identityService", fallbackMethod = "onIdentityUnavailable")
    public void assertIdentityServiceReady() {
        ApiResponse<Map<String, String>> response = identitySystemClient.ping();
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        String status = response.getResult().get("status");
        if (!"UP".equalsIgnoreCase(status)) {
            throw new AppException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    private void onIdentityUnavailable(Throwable throwable) {
        throw new AppException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
}
