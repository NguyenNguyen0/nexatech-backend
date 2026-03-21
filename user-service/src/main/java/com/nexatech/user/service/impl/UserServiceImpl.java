package com.nexatech.user.service.impl;

import java.util.List;

import com.nexatech.user.dto.request.UserCreationRequest;
import com.nexatech.user.dto.request.UserUpdatenRequest;
import com.nexatech.user.dto.response.UserResponse;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;
import com.nexatech.user.mapper.UserMapper;
import com.nexatech.user.model.Role;
import com.nexatech.user.model.User;
import com.nexatech.user.repository.RoleRepository;
import com.nexatech.user.repository.UserRepository;
import com.nexatech.user.service.UserService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserServiceImpl implements UserService {

    UserRepository userRepository;
    RoleRepository roleRepository;
    UserMapper userMapper;

    @NonFinal
    @Value("${keycloak.realm}")
    String realm;

    @NonFinal
    @Value("${keycloak.client-id}")
    String clientId;

    @NonFinal
    @Value("${keycloak.client-secret}")
    String clientSecret;

    @Override
    public UserResponse createUser(UserCreationRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_EMAIL_EXISTS);
        }

        User user = userMapper.toUser(request);

        user.setOnBoarded(false);

        if (request.getRoleId() != null) {
            Role role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
            user.setRole(role);
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

    @Override
    public UserResponse updateUser(String id, UserUpdatenRequest dto) {
        User user = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (dto != null && dto.getEmail() != null && !dto.getEmail().isBlank()) {
            if (!dto.getEmail().equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(dto.getEmail())) {
                throw new AppException(ErrorCode.USER_EMAIL_EXISTS);
            }
        }

        if (dto != null && dto.getRoleId() != null) {
            Role role = roleRepository.findById(dto.getRoleId())
                    .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
            user.setRole(role);
        }

        if (dto != null) {
            userMapper.updateUser(user, dto);
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public void deleteUser(String id) {
        User user = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.setActive(false);
        userRepository.save(user);
    }
}
