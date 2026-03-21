package com.nexatech.identity.client.keycloak;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.nexatech.identity.dto.request.Credential;
import com.nexatech.identity.dto.request.UserCreationParam;
import com.nexatech.identity.dto.request.UserUpdateParam;

import feign.Response;

@FeignClient(name = "keycloak", contextId = "keycloakUser", url = "${keycloak.url}")
public interface KeycloakUserClient {

    @PostMapping(value = "/admin/realms/{realm}/users", consumes = MediaType.APPLICATION_JSON_VALUE)
    Response createUser(@PathVariable("realm") String realm, @RequestHeader("Authorization") String authorization,
            @RequestBody UserCreationParam body);

    @GetMapping("/admin/realms/{realm}/users")
    List<Map<String, Object>> listUsers(@PathVariable("realm") String realm, @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "username", required = false) String username);

    @GetMapping("/admin/realms/{realm}/users/{userId}")
    Map<String, Object> getUserById(@PathVariable("realm") String realm, @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String authorization);

    @PutMapping(value = "/admin/realms/{realm}/users/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    void updateUser(@PathVariable("realm") String realm, @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String authorization, @RequestBody UserUpdateParam body);

    @DeleteMapping("/admin/realms/{realm}/users/{userId}")
    void deleteUser(@PathVariable("realm") String realm, @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String authorization);

    @GetMapping("/admin/realms/{realm}/users/{userId}/credentials")
    List<Map<String, Object>> getCredentials(@PathVariable("realm") String realm, @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String authorization);

    @PutMapping(value = "/admin/realms/{realm}/users/{userId}/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    void resetPassword(@PathVariable("realm") String realm, @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String authorization, @RequestBody Credential body);

    @GetMapping("/admin/realms/{realm}/users/{userId}/role-mappings")
    Map<String, Object> getRoleMappings(@PathVariable("realm") String realm, @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String authorization);

    @GetMapping("/admin/realms/{realm}/users/{userId}/sessions")
    List<Map<String, Object>> getSessions(@PathVariable("realm") String realm, @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String authorization);
}
