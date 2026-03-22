package com.nexatech.identity.service.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nexatech.identity.client.KeycloakAuthClient;
import com.nexatech.identity.dto.request.TokenExchangeParam;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KeycloakAdminTokenProvider {

    private final KeycloakAuthClient keycloakAuthClient;

    @Value("${openfeign.keycloak.realm}")
    private String realm;

    @Value("${openfeign.keycloak.client-id}")
    private String clientId;

    @Value("${openfeign.keycloak.client-secret}")
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
