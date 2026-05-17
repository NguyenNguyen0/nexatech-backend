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
import com.nexatech.identity.dto.request.SocialMediaLoginRequest;
import com.nexatech.identity.dto.request.ForgotPasswordRequest;
import com.nexatech.identity.dto.base.ApiResponse;
import com.nexatech.identity.dto.event.ForgotPasswordEvent;
import com.nexatech.identity.dto.request.LogoutParam;
import com.nexatech.identity.dto.request.LogoutRequest;
import com.nexatech.identity.dto.request.RefreshTokenParam;
import com.nexatech.identity.dto.request.RefreshTokenRequest;
import com.nexatech.identity.dto.request.TokenExchangeParam;
import com.nexatech.identity.dto.request.Credential;
import com.nexatech.identity.dto.request.UserCreationParam;
import com.nexatech.identity.dto.request.UserUpdateParam;
import com.nexatech.identity.dto.request.UserClientRequest;
import com.nexatech.identity.dto.request.UserProfileSyncRequest;
import com.nexatech.identity.dto.request.UserTokenExchangeParam;
import com.nexatech.identity.dto.response.UserTokenExchangeResponse;
import com.nexatech.identity.exception.AppException;
import com.nexatech.identity.exception.ErrorCode;
import com.nexatech.identity.mapper.UserClientMapper;
import com.nexatech.identity.model.SocialMediaType;

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

    private static final String TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange";

    /**
     * Login or auto-register a customer via social provider (Google).
     * NextAuth completes OAuth; this endpoint issues Keycloak tokens for the matched email.
     */
    public UserTokenExchangeResponse loginWithSocialMedia(SocialMediaLoginRequest request) {
        SocialMediaType.from(request.getType());
        String email = request.getUsername().trim().toLowerCase();
        String firstName = blankToNull(request.getFirstName());
        String lastName = blankToNull(request.getLastName());
        String avatarUrl = blankToNull(request.getAvatarUrl());

        Map<String, Object> keycloakUser = findUserByEmail(email);
        String userId;
        String loginUsername;
        String bootstrapPassword = null;
        boolean newUser = false;

        if (keycloakUser == null) {
            newUser = true;
            bootstrapPassword = generateSocialLoginPassword();
            loginUsername = deriveUsernameFromEmail(email);
            UserCreationParam param = UserCreationParam.builder()
                    .username(loginUsername)
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .avatarUrl(avatarUrl)
                    .enabled(true)
                    .emailVerified(true)
                    .role("CUSTOMER")
                    .credentials(List.of(Credential.builder()
                            .type("password")
                            .value(bootstrapPassword)
                            .temporary(false)
                            .build()))
                    .build();
            try {
                userId = register(param);
            } catch (AppException ex) {
                if (ex.getErrorCode() != ErrorCode.USER_EXISTED) {
                    throw ex;
                }
                keycloakUser = findUserByEmail(email);
                if (keycloakUser == null) {
                    throw ex;
                }
                userId = (String) keycloakUser.get("id");
                loginUsername = (String) keycloakUser.get("username");
                if (loginUsername == null || loginUsername.isBlank()) {
                    loginUsername = email;
                }
                bootstrapPassword = null;
                newUser = false;
            }
        } else {
            userId = (String) keycloakUser.get("id");
            loginUsername = (String) keycloakUser.get("username");
            if (loginUsername == null || loginUsername.isBlank()) {
                loginUsername = email;
            }
        }

        assertCustomerRole(userId);
        assertUserAccountActive(userId);
        ensureSocialUserReady(userId, firstName, lastName);
        syncSocialUserProfile(userId, loginUsername, email, firstName, lastName, avatarUrl);

        TokenIssueResult issued = issueUserTokens(userId, loginUsername, email, bootstrapPassword);
        // First-time Google sign-up only; passwordReset is a token fallback, not onboarding.
        boolean requirePasswordChange = newUser || bootstrapPassword != null;
        if (!requirePasswordChange) {
            requirePasswordChange = shouldRequirePasswordChangeForSocialReturn(userId);
        }
        if (requirePasswordChange) {
            markRequiresPasswordChange(userId);
        }

        UserTokenExchangeResponse response = issued.response();
        response.setNewUser(newUser);
        if (requirePasswordChange) {
            response.setRequiresPasswordChange(true);
        } else {
            enrichRequiresPasswordChange(response);
        }
        return response;
    }

    private record TokenIssueResult(UserTokenExchangeResponse response, boolean passwordReset) {}

    /**
     * Map login identifier (username, email, or phone) to Keycloak {@code username} for ROPC.
     */
    private String resolveKeycloakUsername(String identifier) {
        if (identifier.contains("@")) {
            Map<String, Object> keycloakUser = findUserByEmail(identifier.toLowerCase());
            if (keycloakUser != null) {
                return keycloakUsernameFrom(keycloakUser);
            }
        }

        if (isLikelyPhone(identifier)) {
            return resolveUsernameFromUserService(identifier);
        }

        List<Map<String, Object>> users = userService.listUsers(identifier);
        if (users != null) {
            for (Map<String, Object> user : users) {
                String uName = user.get("username") != null ? user.get("username").toString() : null;
                if (identifier.equalsIgnoreCase(uName)) {
                    return uName;
                }
            }
        }

        try {
            return resolveUsernameFromUserService(identifier);
        } catch (AppException ex) {
            if (ex.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                return identifier;
            }
            throw ex;
        }
    }

    private String resolveUsernameFromUserService(String identifier) {
        try {
            ApiResponse<Map<String, Object>> response = userServiceClient.resolveLoginIdentifier(identifier);
            if (response != null && response.getResult() != null) {
                Object username = response.getResult().get("username");
                if (username != null && !username.toString().isBlank()) {
                    return username.toString();
                }
            }
        } catch (FeignException ex) {
            if (ex.status() == 404) {
                throw new AppException(ErrorCode.INVALID_CREDENTIALS);
            }
            throw ex;
        }
        throw new AppException(ErrorCode.INVALID_CREDENTIALS);
    }

    private static String keycloakUsernameFrom(Map<String, Object> keycloakUser) {
        Object username = keycloakUser.get("username");
        if (username == null || username.toString().isBlank()) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        return username.toString();
    }

    private static boolean isLikelyPhone(String raw) {
        String stripped = raw.replaceAll("[\\s\\-().]", "");
        if (stripped.startsWith("+")) {
            stripped = stripped.substring(1);
        }
        return stripped.matches("\\d{9,15}");
    }

    private void ensureSocialUserReady(String userId, String firstName, String lastName) {
        UserUpdateParam.UserUpdateParamBuilder builder = UserUpdateParam.builder()
                .enabled(true)
                .emailVerified(true);
        if (firstName != null) {
            builder.firstName(firstName);
        }
        if (lastName != null) {
            builder.lastName(lastName);
        }
        userService.updateUser(userId, builder.build());
    }

    private void syncSocialUserProfile(
            String userId,
            String username,
            String email,
            String firstName,
            String lastName,
            String avatarUrl) {
        try {
            userServiceClient.syncUserProfile(userId, UserProfileSyncRequest.builder()
                    .email(email)
                    .username(username)
                    .firstName(firstName)
                    .lastName(lastName)
                    .avatarUrl(avatarUrl)
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to sync social profile for user {}: {}", userId, ex.getMessage());
        }
    }

    private void markRequiresPasswordChange(String userId) {
        try {
            userServiceClient.updatePasswordChangeRequired(userId, true);
        } catch (Exception ex) {
            log.warn("Failed to set requiresPasswordChange for user {}: {}", userId, ex.getMessage());
        }
    }

    /** Returning Google users: only when DB flag is still set (first-time setup not completed). */
    private boolean shouldRequirePasswordChangeForSocialReturn(String userId) {
        try {
            ApiResponse<Map<String, Object>> userResponse = userServiceClient.getUserById(userId);
            if (userResponse == null || userResponse.getResult() == null) {
                return false;
            }
            return Boolean.TRUE.equals(userResponse.getResult().get("requiresPasswordChange"));
        } catch (Exception ex) {
            log.warn("Could not read requiresPasswordChange for user {}: {}", userId, ex.getMessage());
            return false;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void assertUserAccountActive(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            userServiceClient.assertLoginAllowed(userId);
        } catch (FeignException ex) {
            if (isUserAccountDisabledFeignError(ex)) {
                throw new AppException(ErrorCode.USER_ACCOUNT_DISABLED);
            }
            throw ex;
        }
    }

    private boolean isUserAccountDisabledFeignError(FeignException ex) {
        if (ex.status() == ErrorCode.USER_ACCOUNT_DISABLED.getStatusCode().value()) {
            return true;
        }
        try {
            String content = ex.contentUTF8();
            if (content == null || content.isBlank()) {
                return false;
            }
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(content);
            return node.has("code")
                    && node.get("code").asInt() == ErrorCode.USER_ACCOUNT_DISABLED.getCode();
        } catch (Exception parseEx) {
            log.trace("Could not parse user-service error body: {}", parseEx.getMessage());
            return false;
        }
    }

    private String resolveUserIdForLogin(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        String trimmed = identifier.trim();
        if (trimmed.contains("@")) {
            Map<String, Object> keycloakUser = findUserByEmail(trimmed.toLowerCase());
            if (keycloakUser != null && keycloakUser.get("id") != null) {
                return keycloakUser.get("id").toString();
            }
        }
        try {
            ApiResponse<Map<String, Object>> response = userServiceClient.resolveLoginIdentifier(trimmed);
            if (response != null && response.getResult() != null) {
                Object userId = response.getResult().get("userId");
                if (userId != null && !userId.toString().isBlank()) {
                    return userId.toString();
                }
            }
        } catch (FeignException ex) {
            if (ex.status() == 404) {
                return null;
            }
            throw ex;
        }
        return null;
    }

    private String extractUserIdFromAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length != 3) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(payload);
            String sub = node.path("sub").asText(null);
            if (sub == null || sub.isBlank()) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
            return sub;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    private Map<String, Object> findUserByEmail(String email) {
        List<Map<String, Object>> users = userService.listUsersByEmail(email);
        if (users == null || users.isEmpty()) {
            return null;
        }
        for (Map<String, Object> user : users) {
            String uEmail = user.get("email") != null ? user.get("email").toString() : null;
            if (email.equalsIgnoreCase(uEmail)) {
                return user;
            }
        }
        return users.getFirst();
    }

    /**
     * Resolve Keycloak user for forgot-password by email, phone, or username
     * (same rules as {@link #resolveKeycloakUsername}).
     */
    private Map<String, Object> findUserByIdentifier(String identifier) {
        if (identifier.contains("@")) {
            return findUserByEmail(identifier.toLowerCase());
        }

        if (isLikelyPhone(identifier)) {
            try {
                String username = resolveUsernameFromUserService(identifier);
                return findKeycloakUserByUsername(username);
            } catch (AppException ex) {
                if (ex.getErrorCode() == ErrorCode.INVALID_CREDENTIALS) {
                    return null;
                }
                throw ex;
            }
        }

        List<Map<String, Object>> users = userService.listUsers(identifier);
        if (users != null) {
            for (Map<String, Object> user : users) {
                String uName = user.get("username") != null ? user.get("username").toString() : null;
                if (identifier.equalsIgnoreCase(uName)) {
                    return user;
                }
            }
        }

        try {
            String username = resolveUsernameFromUserService(identifier);
            return findKeycloakUserByUsername(username);
        } catch (AppException ex) {
            if (ex.getErrorCode() == ErrorCode.INVALID_CREDENTIALS) {
                return null;
            }
            throw ex;
        }
    }

    private Map<String, Object> findKeycloakUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        List<Map<String, Object>> users = userService.listUsers(username);
        if (users == null) {
            return null;
        }
        for (Map<String, Object> user : users) {
            String uName = user.get("username") != null ? user.get("username").toString() : null;
            if (username.equalsIgnoreCase(uName)) {
                return user;
            }
        }
        return null;
    }

    private String deriveUsernameFromEmail(String email) {
        String local = email.substring(0, email.indexOf('@'));
        String base = local.replaceAll("[^a-zA-Z0-9._-]", "").toLowerCase();
        if (base.length() < 4) {
            base = base + "usr";
        }
        if (base.length() > 45) {
            base = base.substring(0, 45);
        }
        String candidate = base;
        int suffix = 0;
        while (userExists(candidate, null)) {
            suffix++;
            String suffixStr = String.valueOf(suffix);
            int maxBase = Math.max(4, 50 - suffixStr.length());
            candidate = base.substring(0, Math.min(base.length(), maxBase)) + suffixStr;
        }
        return candidate;
    }

    private boolean userExists(String username, String excludeUserId) {
        List<Map<String, Object>> users = userService.listUsers(username);
        if (users == null || users.isEmpty()) {
            return false;
        }
        for (Map<String, Object> user : users) {
            String uName = user.get("username") != null ? user.get("username").toString() : null;
            String id = user.get("id") != null ? user.get("id").toString() : null;
            if (username.equalsIgnoreCase(uName) && (excludeUserId == null || !excludeUserId.equals(id))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void assertCustomerRole(String userId) {
        Map<String, Object> mappings = userService.getRoleMappings(userId);
        List<Map<String, Object>> realmMappings = mappings != null
                ? (List<Map<String, Object>>) mappings.get("realmMappings")
                : null;
        boolean customer = realmMappings != null && realmMappings.stream()
                .anyMatch(r -> "CUSTOMER".equalsIgnoreCase(String.valueOf(r.get("name"))));
        if (!customer) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private TokenIssueResult issueUserTokens(
            String userId, String username, String email, String bootstrapPassword) {
        try {
            return new TokenIssueResult(exchangeTokensForUser(userId), false);
        } catch (FeignException ex) {
            log.warn("Token exchange failed for user {} (status {}), trying password grant fallback",
                    userId, ex.status());
            String password = bootstrapPassword;
            boolean passwordReset = false;
            if (password == null) {
                password = generateSocialLoginPassword();
                userService.resetPassword(userId, Credential.builder()
                        .type("password")
                        .value(password)
                        .temporary(false)
                        .build());
                passwordReset = true;
            }
            return new TokenIssueResult(passwordGrantWithFallback(username, email, password), passwordReset);
        }
    }

    private UserTokenExchangeResponse passwordGrantWithFallback(String username, String email, String password) {
        try {
            return passwordGrant(username, password);
        } catch (FeignException ex) {
            if (email != null && !email.isBlank() && !email.equalsIgnoreCase(username)) {
                log.warn("Password grant failed for username {}, retrying with email", username);
                return passwordGrant(email, password);
            }
            throw ex;
        }
    }

    private UserTokenExchangeResponse passwordGrant(String username, String password) {
        return keycloakAuthClient.exchangeUserToken(realm, UserTokenExchangeParam.builder()
                .grant_type("password")
                .client_id(clientId)
                .client_secret(clientSecret)
                .scope("openid")
                .username(username)
                .password(password)
                .build());
    }

    private UserTokenExchangeResponse exchangeTokensForUser(String userId) {
        String serviceToken = keycloakAuthClient.exchangeAdminToken(realm, TokenExchangeParam.builder()
                .grant_type("client_credentials")
                .client_id(clientId)
                .client_secret(clientSecret)
                .scope("openid")
                .build()).getAccessToken();

        return keycloakAuthClient.exchangeUserToken(realm, UserTokenExchangeParam.builder()
                .grant_type(TOKEN_EXCHANGE_GRANT)
                .client_id(clientId)
                .client_secret(clientSecret)
                .scope("openid")
                .subject_token(serviceToken)
                .requested_subject(userId)
                .requested_token_type("urn:ietf:params:oauth:token-type:access_token")
                .audience(clientId)
                .build());
    }

    private void enrichRequiresPasswordChange(UserTokenExchangeResponse response) {
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
            log.warn("Failed to check requiresPasswordChange during social login: {}", ex.getMessage());
        }
    }

    public UserTokenExchangeResponse login(LoginRequest request) {
        if (request == null || request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        String identifier = request.getUsername().trim();
        String userId = resolveUserIdForLogin(identifier);
        if (userId != null) {
            assertUserAccountActive(userId);
        }

        String keycloakUsername = resolveKeycloakUsername(identifier);

        UserTokenExchangeResponse response = keycloakAuthClient.exchangeUserToken(realm, UserTokenExchangeParam.builder()
                .grant_type("password")
                .client_id(clientId)
                .client_secret(clientSecret)
                .scope("openid")
                .username(keycloakUsername)
                .password(request.getPassword())
                .build());

        if (userId == null) {
            userId = extractUserIdFromAccessToken(response.getAccessToken());
        }
        assertUserAccountActive(userId);

        enrichRequiresPasswordChange(response);
        return response;
    }

    public UserTokenExchangeResponse refreshToken(RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        UserTokenExchangeResponse response = keycloakAuthClient.refreshToken(realm, RefreshTokenParam.builder()
                .grant_type("refresh_token")
                .client_id(clientId)
                .client_secret(clientSecret)
                .scope("openid")
                .refresh_token(request.getRefreshToken())
                .build());

        assertUserAccountActive(extractUserIdFromAccessToken(response.getAccessToken()));
        return response;
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

        boolean initialSetup = Boolean.TRUE.equals(request.getInitialSetup());
        boolean requiresChange = resolveRequiresPasswordChange(userId, userInfo);
        boolean skipOldPassword = initialSetup || requiresChange;

        if (!skipOldPassword) {
            if (request.getOldPassword() == null || request.getOldPassword().isBlank()) {
                throw new AppException(ErrorCode.INVALID_PASSWORD);
            }
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
                log.error("Old password verification failed for user {}: {} - {}", username, ex.status(),
                        ex.contentUTF8());
                throw new AppException(ErrorCode.INVALID_PASSWORD);
            }
        }

        userService.resetPassword(userId, com.nexatech.identity.dto.request.Credential.builder()
                .type("password")
                .value(request.getNewPassword())
                .temporary(false)
                .build());

        clearRequiresPasswordChange(userId);
    }

    private boolean resolveRequiresPasswordChange(String userId, Map<String, Object> userInfo) {
        if (Boolean.TRUE.equals(userInfo.get("requiresPasswordChange"))) {
            return true;
        }
        try {
            ApiResponse<Map<String, Object>> userResponse = userServiceClient.getUserById(userId);
            if (userResponse != null && userResponse.getResult() != null) {
                return Boolean.TRUE.equals(userResponse.getResult().get("requiresPasswordChange"));
            }
        } catch (Exception ex) {
            log.warn("Could not load requiresPasswordChange for user {}: {}", userId, ex.getMessage());
        }
        return false;
    }

    private void clearRequiresPasswordChange(String userId) {
        try {
            userServiceClient.updatePasswordChangeRequired(userId, false);
            log.info("Cleared requiresPasswordChange for user {}", userId);
        } catch (Exception ex) {
            log.error("Failed to clear requiresPasswordChange for user {}: {}", userId, ex.getMessage(), ex);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        if (request == null || request.getIdentifier() == null || request.getIdentifier().isBlank()) {
            throw new IllegalArgumentException("Username or Email is required");
        }
        String identifier = request.getIdentifier().trim();
        Map<String, Object> targetUser = findUserByIdentifier(identifier);

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

    /** Alphanumeric only — safe for Feign form-urlencoded password grant. */
    private String generateSocialLoginPassword() {
        int length = 16;
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
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
