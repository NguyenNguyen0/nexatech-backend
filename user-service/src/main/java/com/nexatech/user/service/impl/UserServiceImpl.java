package com.nexatech.user.service.impl;

import java.util.List;

import com.nexatech.user.client.KeycloakClient;
import com.nexatech.user.dto.keycloak.Credential;
import com.nexatech.user.dto.keycloak.LoginRequest;
import com.nexatech.user.dto.keycloak.TokenExchangeParam;
import com.nexatech.user.dto.keycloak.TokenExchangeResponse;
import com.nexatech.user.dto.keycloak.UserCreationParam;
import com.nexatech.user.dto.keycloak.UserTokenExchangeParam;
import com.nexatech.user.dto.keycloak.UserTokenExchangeResponse;
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
import com.nexatech.user.utils.KeycloakUtils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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
    KeycloakClient keycloakClient;

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

        TokenExchangeResponse token = keycloakClient.exchangeToken(
                realm,
                TokenExchangeParam.builder()
                        .grant_type("client_credentials")
                        .client_id(clientId)
                        .client_secret(clientSecret)
                        .scope("openid")
                        .build());

        ResponseEntity<?> userCreationResponse = keycloakClient.createUser(
                realm,
                "Bearer " + token.getAccessToken(),
                UserCreationParam.builder()
                        .username(request.getUsername())
                        .firstName(request.getFirstName())
                        .lastName(request.getLastName())
                        .email(request.getEmail())
                        .enabled(true)
                        .emailVerified(false)
                        .credentials(List.of(Credential.builder()
                                .type("password")
                                .temporary(false)
                                .value(request.getPassword())
                                .build()))
                        .build());

        String userId = KeycloakUtils.extractUserId(userCreationResponse);
        user.setId(userId);

        user.setOnBoarded(request.getOnBoarded() != null ? request.getOnBoarded() : Boolean.FALSE);

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

    @Override
    public UserTokenExchangeResponse login(LoginRequest request) {
        return keycloakClient.exchangeUserToken(realm, UserTokenExchangeParam.builder()
                .grant_type("password")
                .client_id(clientId)
                .client_secret(clientSecret)
                .scope("openid")
                .username(request.getUsername())
                .password(request.getPassword())
                .build());      
    }
}
