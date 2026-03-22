package com.nexatech.identity.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

import com.nexatech.identity.dto.request.IntrospectParam;
import com.nexatech.identity.dto.request.LogoutParam;
import com.nexatech.identity.dto.request.RefreshTokenParam;
import com.nexatech.identity.dto.request.TokenExchangeParam;
import com.nexatech.identity.dto.request.UserTokenExchangeParam;
import com.nexatech.identity.dto.response.TokenExchangeResponse;
import com.nexatech.identity.dto.response.UserTokenExchangeResponse;

import feign.QueryMap;

@FeignClient(name = "keycloak", contextId = "keycloakAuth", url = "${openfeign.keycloak.url}")
public interface KeycloakAuthClient {

    @PostMapping(value = "/realms/{realm}/protocol/openid-connect/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    TokenExchangeResponse exchangeAdminToken(@PathVariable("realm") String realm, @QueryMap TokenExchangeParam param);

    @PostMapping(value = "/realms/{realm}/protocol/openid-connect/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    UserTokenExchangeResponse exchangeUserToken(@PathVariable("realm") String realm, @QueryMap UserTokenExchangeParam param);

    @PostMapping(value = "/realms/{realm}/protocol/openid-connect/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    UserTokenExchangeResponse refreshToken(@PathVariable("realm") String realm, @QueryMap RefreshTokenParam param);

    @PostMapping(value = "/realms/{realm}/protocol/openid-connect/logout", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    void logout(@PathVariable("realm") String realm, @QueryMap LogoutParam param);

    @GetMapping("/realms/{realm}/.well-known/openid-configuration")
    Map<String, Object> getOpenIdConfiguration(@PathVariable("realm") String realm);

    @GetMapping("/realms/{realm}/protocol/openid-connect/userinfo")
    Map<String, Object> userInfo(@PathVariable("realm") String realm, @RequestHeader("Authorization") String authorization);

    @PostMapping(value = "/realms/{realm}/protocol/openid-connect/token/introspect", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    Map<String, Object> introspect(@PathVariable("realm") String realm, @QueryMap IntrospectParam param);
}
