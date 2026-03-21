package com.nexatech.identity.client.keycloak;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.nexatech.identity.dto.request.KeycloakRoleRef;
import com.nexatech.identity.dto.request.RoleCreateParam;

@FeignClient(name = "keycloak", contextId = "keycloakRole", url = "${openfeign.keycloak.url}")
public interface KeycloakRoleClient {

    @PostMapping(value = "/admin/realms/{realm}/roles", consumes = MediaType.APPLICATION_JSON_VALUE)
    void createRole(@PathVariable("realm") String realm, @RequestHeader("Authorization") String authorization,
            @RequestBody RoleCreateParam body);

    @GetMapping("/admin/realms/{realm}/roles")
    List<Map<String, Object>> listRoles(@PathVariable("realm") String realm, @RequestHeader("Authorization") String authorization);

    @GetMapping("/admin/realms/{realm}/roles/{roleName}")
    Map<String, Object> getRoleByName(@PathVariable("realm") String realm, @PathVariable("roleName") String roleName,
            @RequestHeader("Authorization") String authorization);

    @PostMapping(value = "/admin/realms/{realm}/roles/{roleName}/composites", consumes = MediaType.APPLICATION_JSON_VALUE)
    void addCompositeRoles(@PathVariable("realm") String realm, @PathVariable("roleName") String roleName,
            @RequestHeader("Authorization") String authorization, @RequestBody List<KeycloakRoleRef> roles);

    @GetMapping("/admin/realms/{realm}/roles/{roleName}/composites")
    List<Map<String, Object>> getCompositeRoles(@PathVariable("realm") String realm, @PathVariable("roleName") String roleName,
            @RequestHeader("Authorization") String authorization);

    @DeleteMapping(value = "/admin/realms/{realm}/roles/{roleName}/composites", consumes = MediaType.APPLICATION_JSON_VALUE)
    void removeCompositeRoles(@PathVariable("realm") String realm, @PathVariable("roleName") String roleName,
            @RequestHeader("Authorization") String authorization, @RequestBody List<KeycloakRoleRef> roles);

    @PostMapping(value = "/admin/realms/{realm}/users/{userId}/role-mappings/realm", consumes = MediaType.APPLICATION_JSON_VALUE)
    void assignRealmRolesToUser(@PathVariable("realm") String realm, @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String authorization, @RequestBody List<KeycloakRoleRef> roles);

    @DeleteMapping(value = "/admin/realms/{realm}/users/{userId}/role-mappings/realm", consumes = MediaType.APPLICATION_JSON_VALUE)
    void removeRealmRolesFromUser(@PathVariable("realm") String realm, @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String authorization, @RequestBody List<KeycloakRoleRef> roles);

    @GetMapping("/admin/realms/{realm}/roles-by-id/{roleId}")
    Map<String, Object> getRoleById(@PathVariable("realm") String realm, @PathVariable("roleId") String roleId,
            @RequestHeader("Authorization") String authorization);

    @DeleteMapping("/admin/realms/{realm}/roles/{roleName}")
    void deleteRole(@PathVariable("realm") String realm, @PathVariable("roleName") String roleName,
            @RequestHeader("Authorization") String authorization);
}
