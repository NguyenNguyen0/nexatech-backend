package com.nexatech.user.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexatech.user.client.IdentityClient;
import com.nexatech.user.dto.base.ApiResponse;
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

import feign.FeignException;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

        boolean syncKeycloak = dto != null && (isChanged(user.getFirstName(), dto.getFirstName())
                || isChanged(user.getLastName(), dto.getLastName())
                || isChanged(user.getEmail(), dto.getEmail()));

        if (dto != null && isChanged(user.getEmail(), dto.getEmail())) {
            assertEmailAllowedForUpdate(id, user, dto);
        }

        if (dto != null) {
            userMapper.updateUser(user, dto);
        }

        User updatedUser = userRepository.save(user);
        if (syncKeycloak) {
            syncUserToIdentity(updatedUser, true);
        }

        return userMapper.toResponse(updatedUser);
    }

    /**
     * Postgres + Keycloak must agree before we persist a new email (avoids DB/IdP mismatch).
     */
    private void assertEmailAllowedForUpdate(String id, User user, UserUpdatenRequest dto) {
        String newEmail = dto.getEmail() != null ? dto.getEmail().trim() : "";
        if (!isPlausibleEmail(newEmail)) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        if (userRepository.existsByEmailAndIdNot(newEmail, id)) {
            throw new AppException(ErrorCode.USER_EMAIL_EXISTS);
        }
        ApiResponse<Boolean> kc;
        try {
            kc = identityClient.isKeycloakEmailAvailable(newEmail, id);
        } catch (FeignException e) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        }
        if (kc == null || Boolean.FALSE.equals(kc.getResult())) {
            throw new AppException(ErrorCode.USER_EMAIL_EXISTS);
        }
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
        if (!isPlausibleEmail(user.getEmail())) {
            log.warn(
                    "Skipping Keycloak sync for user id={}: email is missing or not a valid address format.",
                    user.getId());
            return;
        }
        try {
            identityClient.updateUser(user.getId(), IdentityUserUpdateRequest.builder()
                    .username(user.getUsername())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .email(user.getEmail())
                    .enabled(enabled)
                    .emailVerified(true)
                    .build());
        } catch (FeignException e) {
            if (e.status() == 404) {
                log.warn(
                        "Identity/Keycloak has no user for id={}; DB change kept (user may predate Keycloak sync).",
                        user.getId());
                return;
            }
            if (e.status() == 409) {
                throw new AppException(ErrorCode.USER_EMAIL_EXISTS);
            }
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        }
    }

    /** Rejects values that are not email-shaped (e.g. UUID accidentally stored as email). */
    private static boolean isPlausibleEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        int at = email.indexOf('@');
        return at > 0 && at < email.length() - 1;
    }

}
