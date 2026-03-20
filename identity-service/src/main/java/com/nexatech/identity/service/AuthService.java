package com.nexatech.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nexatech.identity.client.KeycloakClient;
import com.nexatech.identity.dto.keycloak.LoginRequest;
import com.nexatech.identity.dto.keycloak.LogoutParam;
import com.nexatech.identity.dto.keycloak.LogoutRequest;
import com.nexatech.identity.dto.keycloak.RefreshTokenParam;
import com.nexatech.identity.dto.keycloak.RefreshTokenRequest;
import com.nexatech.identity.dto.keycloak.UserTokenExchangeParam;
import com.nexatech.identity.dto.keycloak.UserTokenExchangeResponse;

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
}

