package com.nexatech.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import com.nexatech.identity.client.KeycloakAuthClient;
import com.nexatech.identity.dto.request.KeycloakRoleRef;
import com.nexatech.identity.dto.request.IntrospectParam;
import com.nexatech.identity.dto.request.IntrospectTokenRequest;
import com.nexatech.identity.dto.request.LoginRequest;
import com.nexatech.identity.dto.request.ForgotPasswordRequest;
import com.nexatech.identity.dto.base.ApiResponse;
import com.nexatech.identity.dto.event.ForgotPasswordEvent;
import com.nexatech.identity.dto.request.LogoutParam;
import com.nexatech.identity.dto.request.LogoutRequest;
import com.nexatech.identity.dto.request.RefreshTokenParam;
import com.nexatech.identity.dto.request.RefreshTokenRequest;
import com.nexatech.identity.dto.request.UserCreationParam;
import com.nexatech.identity.dto.request.UserClientRequest;
import com.nexatech.identity.dto.request.UserTokenExchangeParam;
import com.nexatech.identity.dto.response.UserTokenExchangeResponse;
import com.nexatech.identity.exception.AppException;
import com.nexatech.identity.exception.ErrorCode;
import com.nexatech.identity.mapper.UserClientMapper;

import feign.FeignException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final KeycloakAuthClient keycloakAuthClient;
    private final UserService userService;
    private final RoleService roleService;
    private final UserServiceDependencyGuard userServiceDependencyGuard;
    private final KafkaService kafkaService;
    private final UserClientMapper userClientMapper;
    private final com.nexatech.identity.client.UserServiceClient userServiceClient;

    @Value("${openfeign.keycloak.realm}")
    private String realm;

    @Value("${openfeign.keycloak.client-id}")
    private String clientId;

    @Value("${openfeign.keycloak.client-secret}")
    private String clientSecret;

    public String register(UserCreationParam param) {
        userServiceDependencyGuard.assertUserServiceReady();
        String createdUserId = userService.createUser(param);
        try {
            // Assign requested realm role to the newly created Keycloak user.
            // We resolve roleName -> (id, name) using RoleService (Keycloak API).
            String roleName = (param != null ? param.getRole() : null);
            if (roleName == null || roleName.isBlank()) {
                roleName = "CUSTOMER";
            }
            String trimmedRoleName = roleName.trim();

            Map<String, Object> role;
            try {
                role = roleService.getRoleByName(trimmedRoleName);
            } catch (FeignException ex) {
                // Fallback for potential casing mismatch.
                if (ex.status() == 404) {
                    String lower = trimmedRoleName.toLowerCase();
                    String upper = trimmedRoleName.toUpperCase();
                    Map<String, Object> fallbackRole = null;
                    try {
                        fallbackRole = roleService.getRoleByName(lower);
                    } catch (FeignException ignored) {
                    }
                    if (fallbackRole == null) {
                        try {
                            fallbackRole = roleService.getRoleByName(upper);
                        } catch (FeignException ignored) {
                        }
                    }
                    if (fallbackRole == null) {
                        throw ex;
                    }
                    role = fallbackRole;
                } else {
                    throw ex;
                }
            }

            Object roleIdObj = role != null ? role.get("id") : null;
            String resolvedRoleName = role != null && role.get("name") != null ? role.get("name").toString()
                    : trimmedRoleName;
            String roleId = roleIdObj != null ? roleIdObj.toString() : null;
            if (roleId == null || roleId.isBlank()) {
                throw new IllegalArgumentException("Keycloak role id is missing for role=" + resolvedRoleName);
            }

            userService.assignRealmRolesToUser(createdUserId,
                    List.of(new KeycloakRoleRef(roleId, resolvedRoleName)));

            UserClientRequest request = userClientMapper.toProvisionRequest(createdUserId, param);
            kafkaService.sendUserRegistered(request);
            return createdUserId;
        } catch (AppException ex) {
            userService.deleteUser(createdUserId);
            throw ex;
        } catch (FeignException ex) {
            userService.deleteUser(createdUserId);
            throw ex;
        } catch (Exception ex) {
            userService.deleteUser(createdUserId);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    public UserTokenExchangeResponse login(LoginRequest request) {
        if (request == null || request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        UserTokenExchangeResponse response = keycloakAuthClient.exchangeUserToken(realm, UserTokenExchangeParam.builder()
                .grant_type("password")
                .client_id(clientId)
                .client_secret(clientSecret)
                .scope("openid")
                .username(request.getUsername())
                .password(request.getPassword())
                .build());

        // Decode token to get userId and check requiresPasswordChange
        try {
            String accessToken = response.getAccessToken();
            String[] parts = accessToken.split("\\.");
            if (parts.length == 3) {
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(payload);
                String userId = node.get("sub").asText();
                
                ApiResponse<Map<String, Object>> userResponse = userServiceClient.getUserById(userId);
                if (userResponse != null && userResponse.getResult() != null) {
                    Object req = userResponse.getResult().get("requiresPasswordChange");
                    if (req instanceof Boolean) {
                        response.setRequiresPasswordChange((Boolean) req);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to check requiresPasswordChange during login: {}", ex.getMessage());
        }

        return response;
    }

    public UserTokenExchangeResponse refreshToken(RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        return keycloakAuthClient.refreshToken(realm, RefreshTokenParam.builder()
                .grant_type("refresh_token")
                .client_id(clientId)
                .client_secret(clientSecret)
                .scope("openid")
                .refresh_token(request.getRefreshToken())
                .build());
    }

    public void logout(LogoutRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        keycloakAuthClient.logout(realm, LogoutParam.builder()
                .client_id(clientId)
                .client_secret(clientSecret)
                .refresh_token(request.getRefreshToken())
                .build());
    }

    public Map<String, Object> getOpenIdConfiguration() {
        return keycloakAuthClient.getOpenIdConfiguration(realm);
    }

    public Map<String, Object> userInfo(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new IllegalArgumentException("Authorization header is required");
        }
        if (!authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new IllegalArgumentException("Authorization must be a Bearer token");
        }
        Map<String, Object> kcUserInfo = keycloakAuthClient.userInfo(realm, authorizationHeader);

        // Enrich with requiresPasswordChange from user-service
        try {
            String userId = (String) kcUserInfo.get("sub");
            if (userId != null) {
                ApiResponse<Map<String, Object>> userResponse = userServiceClient.getUserById(userId);
                if (userResponse != null && userResponse.getResult() != null) {
                    Object req = userResponse.getResult().get("requiresPasswordChange");
                    if (req != null) {
                        kcUserInfo.put("requiresPasswordChange", req);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to enrich user info with requiresPasswordChange: {}", ex.getMessage());
        }

        return kcUserInfo;
    }

    public Map<String, Object> introspect(IntrospectTokenRequest request) {
        if (request == null || request.getToken() == null || request.getToken().isBlank()) {
            throw new IllegalArgumentException("token is required");
        }
        return keycloakAuthClient.introspect(realm, IntrospectParam.builder()
                .client_id(clientId)
                .client_secret(clientSecret)
                .token(request.getToken())
                .build());
    }

    public void changePassword(String authorizationHeader,
            com.nexatech.identity.dto.request.PasswordChangeRequest request) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // 1. Get current user info to get userId (sub) and username
        Map<String, Object> userInfo = userInfo(authorizationHeader);
        String userId = (String) userInfo.get("sub");
        String username = (String) userInfo.get("preferred_username");
        if (username == null) {
            username = (String) userInfo.get("email");
        }

        // 2. Verify old password by trying to login
        try {
            keycloakAuthClient.exchangeUserToken(realm, UserTokenExchangeParam.builder()
                    .grant_type("password")
                    .client_id(clientId)
                    .client_secret(clientSecret)
                    .scope("openid")
                    .username(username)
                    .password(request.getOldPassword())
                    .build());
        } catch (FeignException ex) {
            log.error("Old password verification failed for user {}: {} - {}", username, ex.status(), ex.contentUTF8());
            throw new AppException(ErrorCode.INVALID_PASSWORD);
        }

        // 3. Reset password using Admin API (which the service has access to)
        userService.resetPassword(userId, com.nexatech.identity.dto.request.Credential.builder()
                .type("password")
                .value(request.getNewPassword())
                .temporary(false)
                .build());

        // 4. Clear requiresPasswordChange flag
        try {
            userServiceClient.updatePasswordChangeRequired(userId, false);
        } catch (Exception ex) {
            log.warn("Failed to clear requiresPasswordChange for user {}: {}", userId, ex.getMessage());
        }
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        if (request == null || request.getIdentifier() == null || request.getIdentifier().isBlank()) {
            throw new IllegalArgumentException("Username or Email is required");
        }
        String identifier = request.getIdentifier().trim();
        List<Map<String, Object>> users = userService.listUsers(identifier);

        // Filter exactly matching username or email since Keycloak's search might be
        // fuzzy
        Map<String, Object> targetUser = null;
        if (users != null) {
            for (Map<String, Object> user : users) {
                String uName = (String) user.get("username");
                String uEmail = (String) user.get("email");
                if (identifier.equalsIgnoreCase(uName) || identifier.equalsIgnoreCase(uEmail)) {
                    targetUser = user;
                    break;
                }
            }
        }

        if (targetUser == null) {
            log.warn("Forgot password requested for non-existent user: {}", identifier);
            // Return normally to prevent user enumeration attacks
            return;
        }

        String userId = (String) targetUser.get("id");
        String username = (String) targetUser.get("username");
        String email = (String) targetUser.get("email");
        String firstName = (String) targetUser.get("firstName");
        String lastName = (String) targetUser.get("lastName");

        if (email == null || email.isBlank()) {
            log.warn("Forgot password requested for user without email: {}", identifier);
            return;
        }

        // Generate a random 8-character password
        String newPassword = generateRandomPassword();

        // Reset password
        userService.resetPassword(userId, com.nexatech.identity.dto.request.Credential.builder()
                .type("password")
                .value(newPassword)
                .temporary(false)
                .build());

        // Publish event
        String displayName = (firstName != null && lastName != null) ? firstName.trim() + " " + lastName.trim()
                : username;
        ForgotPasswordEvent event = ForgotPasswordEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType("FORGOT_PASSWORD")
                .timestamp(java.time.Instant.now())
                .userId(userId)
                .email(email)
                .displayName(displayName)
                .newPassword(newPassword)
                .build();

        kafkaService.sendForgotPasswordEvent(event);

        // Set requiresPasswordChange flag
        try {
            userServiceClient.updatePasswordChangeRequired(userId, true);
        } catch (Exception ex) {
            log.warn("Failed to set requiresPasswordChange for user {}: {}", userId, ex.getMessage());
        }

        log.info("Reset password and sent forgot password event for user: {}", identifier);
    }

    private String generateRandomPassword() {
        int length = 8;
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
