package com.nexatech.user.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexatech.user.client.IdentityClient;
import com.nexatech.user.dto.request.IdentityUserUpdateRequest;
import com.nexatech.user.dto.request.UserCreationRequest;
import com.nexatech.user.dto.request.UserUpdatenRequest;
import com.nexatech.user.dto.response.UserResponse;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;
import com.nexatech.user.mapper.UserMapper;
import com.nexatech.user.model.User;
import com.nexatech.user.repository.UserRepository;
import com.nexatech.user.service.UserService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserServiceImpl implements UserService {

    UserRepository userRepository;
    UserMapper userMapper;
    IdentityClient identityClient;

    @Override
    public UserResponse createUser(UserCreationRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_EMAIL_EXISTS);
        }

        User user = userMapper.toUser(request);
        if (request.getId() != null && !request.getId().isBlank()) {
            user.setId(request.getId());
        } else {
            user.setId(UUID.randomUUID().toString());
        }
        if (user.getOnBoarded() == null) {
            user.setOnBoarded(false);
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAllByActiveTrue()
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    public UserResponse getUserById(String id) {
        User user = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toResponse(user);
    }

    @Transactional
    @Override
    public UserResponse updateUser(String id, UserUpdatenRequest dto) {
        User user = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (dto != null && dto.getEmail() != null && !dto.getEmail().isBlank()) {
            if (!dto.getEmail().equalsIgnoreCase(user.getEmail())) {
                throw new AppException(ErrorCode.EMAIL_UPDATE_NOT_ALLOWED);
            }
        }

        boolean syncKeycloak = dto != null && (isChanged(user.getFirstName(), dto.getFirstName())
                || isChanged(user.getLastName(), dto.getLastName()));

        if (dto != null) {
            userMapper.updateUser(user, dto);
        }

        User updatedUser = userRepository.save(user);
        if (syncKeycloak) {
            syncUserToIdentity(updatedUser, true);
        }

        return userMapper.toResponse(updatedUser);
    }

    @Transactional
    @Override
    public void deleteUser(String id) {
        User user = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.setActive(false);
        User deletedUser = userRepository.save(user);
        syncUserToIdentity(deletedUser, false);
    }

    @Transactional
    @Override
    public void enableUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.setActive(true);
        User enabledUser = userRepository.save(user);
        syncUserToIdentity(enabledUser, true);
    }

    // private method: check if the value is changed
    private boolean isChanged(String currentValue, String newValue) {
        return newValue != null && !newValue.isBlank() && !Objects.equals(currentValue, newValue);
    }

    // private method: UPDATE OR DELETE
    private void syncUserToIdentity(User user, boolean enabled) {
        try {
            identityClient.updateUser(user.getId(), IdentityUserUpdateRequest.builder()
                    .username(user.getUsername())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .email(user.getEmail())
                    .enabled(enabled)
                    .emailVerified(true)
                    .build());
        } catch (Exception ex) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        }
    }
}
