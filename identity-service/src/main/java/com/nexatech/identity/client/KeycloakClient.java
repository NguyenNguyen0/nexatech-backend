package com.nexatech.identity.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.nexatech.identity.dto.request.LogoutParam;
import com.nexatech.identity.dto.request.RefreshTokenParam;
import com.nexatech.identity.dto.request.TokenExchangeParam;
import com.nexatech.identity.dto.request.UserCreationParam;
import com.nexatech.identity.dto.request.UserTokenExchangeParam;
import com.nexatech.identity.dto.request.UserUpdateParam;
import com.nexatech.identity.dto.response.TokenExchangeResponse;
import com.nexatech.identity.dto.response.UserTokenExchangeResponse;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import feign.QueryMap;

@FeignClient(name = "keycloak-client", url = "${keycloak.url}")
public interface KeycloakClient {

    @PostMapping(value = "/realms/{realm-name}/protocol/openid-connect/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    TokenExchangeResponse exchangeAdminToken(@PathVariable("realm-name") String realm, @QueryMap TokenExchangeParam param);

    @PostMapping(value = "/realms/{realm-name}/protocol/openid-connect/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    UserTokenExchangeResponse exchangeUserToken(@PathVariable("realm-name") String realm, @QueryMap UserTokenExchangeParam param);

    @PostMapping(value = "/realms/{realm-name}/protocol/openid-connect/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    UserTokenExchangeResponse refreshToken(@PathVariable("realm-name") String realm, @QueryMap RefreshTokenParam param);

    @PostMapping(value = "/realms/{realm-name}/protocol/openid-connect/logout", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<?> logout(@PathVariable("realm-name") String realm, @QueryMap LogoutParam param);

    @PostMapping(value = "/admin/realms/{realm-name}/users", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> onboardUser(@PathVariable("realm-name") String realm, @RequestHeader("authorization") String token, @RequestBody UserCreationParam param);

    @PutMapping(value = "/admin/realms/{realm-name}/users/{user-id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> updateUser(@PathVariable("realm-name") String realm, @PathVariable("user-id") String userId, @RequestHeader("authorization") String token, @RequestBody UserUpdateParam param);
}
