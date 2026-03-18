package com.nexatech.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.nexatech.user.dto.keycloak.TokenExchangeParam;
import com.nexatech.user.dto.keycloak.TokenExchangeResponse;
import com.nexatech.user.dto.keycloak.UserCreationParam;
import com.nexatech.user.dto.keycloak.UserTokenExchangeParam;
import com.nexatech.user.dto.keycloak.UserTokenExchangeResponse;

import feign.QueryMap;

@FeignClient(name = "keycloak-client", url = "${keycloak.url}")
public interface KeycloakClient {

    @PostMapping(value = "/realms/{realm-name}/protocol/openid-connect/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    TokenExchangeResponse exchangeToken(@PathVariable("realm-name") String realm, @QueryMap TokenExchangeParam param);

    @PostMapping(value = "/admin/realms/{realm-name}/users", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> createUser(@PathVariable("realm-name") String realm, @RequestHeader("authorization") String token, @RequestBody UserCreationParam param);

    @PostMapping(value = "/realms/{realm-name}/protocol/openid-connect/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    UserTokenExchangeResponse exchangeUserToken(@PathVariable("realm-name") String realm, @QueryMap UserTokenExchangeParam param);
}
