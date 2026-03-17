package com.nexatech.user.service;

import com.nexatech.user.dto.UpdateProfileRequest;
import com.nexatech.user.dto.UserProfileResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserProfileService {

    public UserProfileResponse getProfile(UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Page<UserProfileResponse> getAllUsers(Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void deactivateUser(UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
