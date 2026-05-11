package com.nexatech.identity.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.nexatech.identity.client.UserServiceClient;
import com.nexatech.identity.dto.base.ApiResponse;
import com.nexatech.identity.exception.AppException;
import com.nexatech.identity.exception.ErrorCode;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceDependencyGuard {

    private final UserServiceClient userServiceClient;

    @Retry(name = "userServiceAvailability")
    @CircuitBreaker(name = "userServiceAvailability", fallbackMethod = "onUserServiceUnavailable")
    public void assertUserServiceReady() {
        ApiResponse<Map<String, String>> response = userServiceClient.ping();
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        String status = response.getResult().get("status");
        if (!"UP".equalsIgnoreCase(status)) {
            throw new AppException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    private void onUserServiceUnavailable(Throwable throwable) {
        throw new AppException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
}
