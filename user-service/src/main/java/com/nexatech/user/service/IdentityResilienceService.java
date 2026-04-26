package com.nexatech.user.service;

import org.springframework.stereotype.Service;

import com.nexatech.user.client.IdentityClient;
import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.event.KeycloakUserProvisionPayload;
import com.nexatech.user.dto.request.IdentityUserUpdateRequest;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IdentityResilienceService {

    private final IdentityClient identityClient;

    @Retry(name = "identityService")
    @CircuitBreaker(name = "identityService", fallbackMethod = "onCreateUserFailure")
    public ApiResponse<String> createKeycloakUser(KeycloakUserProvisionPayload payload) {
        return identityClient.createKeycloakUser(payload);
    }

    @Retry(name = "identityService")
    @CircuitBreaker(name = "identityService", fallbackMethod = "onCheckEmailFailure")
    public ApiResponse<Boolean> isKeycloakEmailAvailable(String email, String excludeUserId) {
        return identityClient.isKeycloakEmailAvailable(email, excludeUserId);
    }

    @Retry(name = "identityService")
    @CircuitBreaker(name = "identityService", fallbackMethod = "onUpdateUserFailure")
    public void updateIdentityUser(String userId, IdentityUserUpdateRequest request) {
        identityClient.updateUser(userId, request);
    }

    private ApiResponse<String> onCreateUserFailure(KeycloakUserProvisionPayload payload, Throwable throwable) {
        throw new AppException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    private ApiResponse<Boolean> onCheckEmailFailure(String email, String excludeUserId, Throwable throwable) {
        throw new AppException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    private void onUpdateUserFailure(String userId, IdentityUserUpdateRequest request, Throwable throwable) {
        throw new AppException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
}
