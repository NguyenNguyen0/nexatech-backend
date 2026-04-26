package com.nexatech.user.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexatech.user.dto.event.KeycloakCredentialPayload;
import com.nexatech.user.dto.event.KeycloakUserProvisionPayload;
import com.nexatech.user.dto.request.AdminUserCreateRequest;
import com.nexatech.user.dto.request.UserCreationRequest;
import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.response.UserResponse;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;
import com.nexatech.user.mapper.UserMapper;
import com.nexatech.user.model.User;
import com.nexatech.user.repository.UserRepository;
import com.nexatech.user.service.AdminUserService;
import com.nexatech.user.service.IdentityResilienceService;
import com.nexatech.user.service.IdentityServiceDependencyGuard;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserServiceImpl implements AdminUserService {

    UserRepository userRepository;
    UserMapper userMapper;
    IdentityResilienceService identityResilienceService;
    IdentityServiceDependencyGuard identityServiceDependencyGuard;

    @Override
    @Transactional
    public UserResponse createUserWithKeycloakProvision(AdminUserCreateRequest request) {
        identityServiceDependencyGuard.assertIdentityServiceReady();
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

        String requestedId = request.getId() != null && !request.getId().isBlank() ? request.getId() : null;

        boolean enabled = request.getEnabled() != null ? request.getEnabled() : true;
        boolean emailVerified = Boolean.TRUE.equals(request.getEmailVerified());
        boolean temporaryPassword = Boolean.TRUE.equals(request.getTemporaryPassword());

        KeycloakUserProvisionPayload provision = KeycloakUserProvisionPayload.builder()
                .id(requestedId)
                .username(request.getUsername())
                .enabled(enabled)
                .email(request.getEmail())
                .emailVerified(emailVerified)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .credentials(List.of(KeycloakCredentialPayload.builder()
                        .type("password")
                        .value(request.getPassword())
                        .temporary(temporaryPassword)
                        .build()))
                .build();

        ApiResponse<String> created;
        try {
            created = identityResilienceService.createKeycloakUser(provision);
        } catch (Exception ex) {
            if (ex instanceof AppException appException && appException.getErrorCode() == ErrorCode.DEPENDENCY_UNAVAILABLE) {
                throw appException;
            }
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        }
        String keycloakUserId = created != null ? created.getResult() : null;
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        }

        UserCreationRequest profile = UserCreationRequest.builder()
                .id(keycloakUserId)
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
        user.setId(keycloakUserId);
        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }
}
