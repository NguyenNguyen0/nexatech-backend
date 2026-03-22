package com.nexatech.user.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexatech.user.client.IdentityClient;
import com.nexatech.user.dto.event.KeycloakCredentialPayload;
import com.nexatech.user.dto.event.KeycloakUserProvisionPayload;
import com.nexatech.user.dto.request.AdminUserCreateRequest;
import com.nexatech.user.dto.request.UserCreationRequest;
import com.nexatech.user.dto.response.UserResponse;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;
import com.nexatech.user.mapper.UserMapper;
import com.nexatech.user.model.User;
import com.nexatech.user.repository.UserRepository;
import com.nexatech.user.service.AdminUserService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserServiceImpl implements AdminUserService {

    UserRepository userRepository;
    UserMapper userMapper;
    IdentityClient identityClient;

    @Override
    @Transactional
    public UserResponse createUserWithKeycloakProvision(AdminUserCreateRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new AppException(ErrorCode.USERNAME_IS_MISSING);
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_EMAIL_EXISTS);
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        String userId = request.getId() != null && !request.getId().isBlank()
                ? request.getId()
                : UUID.randomUUID().toString();

        UserCreationRequest profile = UserCreationRequest.builder()
                .id(userId)
                .email(request.getEmail())
                .username(request.getUsername())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .avatarUrl(request.getAvatarUrl())
                .phone(request.getPhone())
                .dob(request.getDob())
                .gender(request.getGender())
                .onBoarded(request.getOnBoarded() != null ? request.getOnBoarded() : false)
                .build();

        User user = userMapper.toUser(profile);
        user.setId(userId);
        User saved = userRepository.save(user);

        KeycloakUserProvisionPayload provision = KeycloakUserProvisionPayload.builder()
                .id(saved.getId())
                .username(saved.getUsername())
                .enabled(request.isEnabled())
                .email(saved.getEmail())
                .emailVerified(request.isEmailVerified())
                .firstName(saved.getFirstName())
                .lastName(saved.getLastName())
                .credentials(List.of(KeycloakCredentialPayload.builder()
                        .type("password")
                        .value(request.getPassword())
                        .temporary(request.isTemporaryPassword())
                        .build()))
                .build();

        try {
            identityClient.createKeycloakUser(provision);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        }
        return userMapper.toResponse(saved);
    }
}
