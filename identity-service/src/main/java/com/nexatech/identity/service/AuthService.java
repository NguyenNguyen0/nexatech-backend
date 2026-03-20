package com.nexatech.identity.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.nexatech.identity.client.KeycloakClient;
import com.nexatech.identity.dto.keycloak.Credential;
import com.nexatech.identity.dto.keycloak.LoginRequest;
import com.nexatech.identity.dto.keycloak.LogoutParam;
import com.nexatech.identity.dto.keycloak.LogoutRequest;
import com.nexatech.identity.dto.keycloak.RefreshTokenParam;
import com.nexatech.identity.dto.keycloak.RefreshTokenRequest;
import com.nexatech.identity.dto.keycloak.TokenExchangeParam;
import com.nexatech.identity.dto.keycloak.TokenExchangeResponse;
import com.nexatech.identity.dto.keycloak.UserCreationParam;
import com.nexatech.identity.dto.keycloak.UserTokenExchangeParam;
import com.nexatech.identity.dto.keycloak.UserTokenExchangeResponse;
import com.nexatech.identity.dto.keycloak.UserUpdateParam;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KeycloakClient keycloakClient;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    public UserTokenExchangeResponse login(LoginRequest request) {
        if (request == null || request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        return keycloakClient.exchangeUserToken(realm, UserTokenExchangeParam.builder()
                .grant_type("password")
                .client_id(clientId)
                .client_secret(clientSecret)
                .scope("openid")
                .username(request.getUsername())
                .password(request.getPassword())
                .build());
    }

    public UserTokenExchangeResponse refreshToken(RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        return keycloakClient.refreshToken(realm, RefreshTokenParam.builder()
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

        keycloakClient.logout(realm, LogoutParam.builder()
                .client_id(clientId)
                .client_secret(clientSecret)
                .refresh_token(request.getRefreshToken())
                .build());
    }

    public ResponseEntity<?> onboardUser(UserCreationParam param) {
        if (param == null || param.getCredentials() == null || param.getCredentials().isEmpty()) {
            throw new IllegalArgumentException("credentials is required");
        }

        return keycloakClient.onboardUser(realm, "Bearer " + exchangeToken().getAccessToken(),
                UserCreationParam.builder()
                        .username(param.getUsername())
                        .firstName(param.getFirstName())
                        .lastName(param.getLastName())
                        .email(param.getEmail())
                        .enabled(true)
                        .emailVerified(false)
                        .credentials(List.of(Credential.builder()
                                .type(param.getCredentials().get(0).getType())
                                .value(param.getCredentials().get(0).getValue())
                                .temporary(param.getCredentials().get(0).isTemporary())
                                .build()))
                        .build());
    }

    public ResponseEntity<?> updateUser(String userId, UserUpdateParam param) {
        return keycloakClient.updateUser(realm, userId, "Bearer " + exchangeToken().getAccessToken(),
                UserUpdateParam.builder()
                        .username(param.getUsername())
                        .firstName(param.getFirstName())
                        .lastName(param.getLastName())
                        .email(param.getEmail())
                        .enabled(param.isEnabled())
                        .emailVerified(param.isEmailVerified())
                        .build());
    }

    // internal method
    private TokenExchangeResponse exchangeToken() {
        return keycloakClient.exchangeAdminToken(realm, TokenExchangeParam.builder()
                .grant_type("client_credentials")
                .client_id(clientId)
                .client_secret(clientSecret)
                .scope("openid")
                .build());
    }
}
