package com.nexatech.user.service;

import com.nexatech.user.dto.base.PageResponse;
import com.nexatech.user.dto.request.UserCreationRequest;
import com.nexatech.user.dto.request.UserProfileSyncRequest;
import com.nexatech.user.dto.request.UserUpdatenRequest;
import com.nexatech.user.dto.response.OnboardingStatusResponse;
import com.nexatech.user.dto.response.UserResponse;

public interface UserService {
    
    UserResponse createUser(UserCreationRequest request);

    PageResponse<UserResponse> getAllUsers(
            int page,
            int size,
            String email,
            String phone,
            Boolean active,
            String role);

    UserResponse getUserById(String id);

    UserResponse updateUser(String id, UserUpdatenRequest dto);

    void deleteUser(String id);

    void disableUser(String id);

    void enableUser(String id);

    OnboardingStatusResponse getOnboardingStatus(String userId);

    void requestEmailVerificationOtp(String userId);

    void verifyEmailVerificationOtp(String userId, String otp);
    
    void updatePasswordChangeRequired(String userId, boolean required);

    UserResponse syncProfileFromSocialLogin(String userId, UserProfileSyncRequest request);

    /** Resolve login identifier (username / email / phone) to Keycloak username. */
    java.util.Map<String, String> resolveLoginIdentifier(String identifier);
}
