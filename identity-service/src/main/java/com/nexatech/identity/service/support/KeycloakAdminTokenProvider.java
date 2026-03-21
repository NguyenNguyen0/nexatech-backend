package com.nexatech.identity.service.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nexatech.identity.client.keycloak.KeycloakAuthClient;
import com.nexatech.identity.dto.request.TokenExchangeParam;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KeycloakAdminTokenProvider {

    private final KeycloakAuthClient keycloakAuthClient;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    public String adminAuthorizationHeader() {
        return "Bearer " + keycloakAuthClient.exchangeAdminToken(realm, TokenExchangeParam.builder()
                .grant_type("client_credentials")
                .client_id(clientId)
                .client_secret(clientSecret)
                .scope("openid")
                .build()).getAccessToken();
    }
}
