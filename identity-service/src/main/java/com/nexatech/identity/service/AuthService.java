package com.nexatech.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

import com.nexatech.identity.client.keycloak.KeycloakAuthClient;
import com.nexatech.identity.dto.request.IntrospectParam;
import com.nexatech.identity.dto.request.IntrospectTokenRequest;
import com.nexatech.identity.dto.request.LoginRequest;
import com.nexatech.identity.dto.request.LogoutParam;
import com.nexatech.identity.dto.request.LogoutRequest;
import com.nexatech.identity.dto.request.RefreshTokenParam;
import com.nexatech.identity.dto.request.RefreshTokenRequest;
import com.nexatech.identity.dto.request.UserTokenExchangeParam;
import com.nexatech.identity.dto.response.UserTokenExchangeResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KeycloakAuthClient keycloakAuthClient;

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

        return keycloakAuthClient.exchangeUserToken(realm, UserTokenExchangeParam.builder()
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
        return keycloakAuthClient.userInfo(realm, authorizationHeader);
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
}
