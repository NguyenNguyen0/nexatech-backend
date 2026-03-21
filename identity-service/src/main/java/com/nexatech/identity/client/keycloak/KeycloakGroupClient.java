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
import org.springframework.web.bind.annotation.RequestParam;

import com.nexatech.identity.dto.request.GroupCreateParam;
import com.nexatech.identity.dto.request.KeycloakRoleRef;

import feign.Response;

@FeignClient(name = "keycloak", contextId = "keycloakGroup", url = "${keycloak.url}")
public interface KeycloakGroupClient {

    @PostMapping(value = "/admin/realms/{realm}/groups", consumes = MediaType.APPLICATION_JSON_VALUE)
    Response createGroup(@PathVariable("realm") String realm, @RequestHeader("Authorization") String authorization,
            @RequestBody GroupCreateParam body);

    @GetMapping("/admin/realms/{realm}/groups")
    List<Map<String, Object>> listGroups(@PathVariable("realm") String realm, @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "search", required = false) String search);

    @GetMapping("/admin/realms/{realm}/groups/{groupId}/role-mappings")
    Map<String, Object> getGroupRoleMappings(@PathVariable("realm") String realm, @PathVariable("groupId") String groupId,
            @RequestHeader("Authorization") String authorization);

    @PostMapping(value = "/admin/realms/{realm}/groups/{groupId}/role-mappings/realm", consumes = MediaType.APPLICATION_JSON_VALUE)
    void assignRealmRolesToGroup(@PathVariable("realm") String realm, @PathVariable("groupId") String groupId,
            @RequestHeader("Authorization") String authorization, @RequestBody List<KeycloakRoleRef> roles);

    @DeleteMapping(value = "/admin/realms/{realm}/groups/{groupId}/role-mappings/realm", consumes = MediaType.APPLICATION_JSON_VALUE)
    void removeRealmRolesFromGroup(@PathVariable("realm") String realm, @PathVariable("groupId") String groupId,
            @RequestHeader("Authorization") String authorization, @RequestBody List<KeycloakRoleRef> roles);

    @GetMapping("/admin/realms/{realm}/groups/{groupId}/members")
    List<Map<String, Object>> getGroupMembers(@PathVariable("realm") String realm, @PathVariable("groupId") String groupId,
            @RequestHeader("Authorization") String authorization);

    @DeleteMapping("/admin/realms/{realm}/groups/{groupId}")
    void deleteGroup(@PathVariable("realm") String realm, @PathVariable("groupId") String groupId,
            @RequestHeader("Authorization") String authorization);
}
