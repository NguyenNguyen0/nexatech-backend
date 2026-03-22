package com.nexatech.identity.client;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "keycloak", contextId = "keycloakGroupUser", url = "${openfeign.keycloak.url}")
public interface KeycloakGroupUserClient {

    @PutMapping("/admin/realms/{realm}/users/{userId}/groups/{groupId}")
    void addUserToGroup(@PathVariable("realm") String realm, @PathVariable("userId") String userId,
            @PathVariable("groupId") String groupId, @RequestHeader("Authorization") String authorization);

    @GetMapping("/admin/realms/{realm}/users/{userId}/groups")
    List<Map<String, Object>> getGroupsForUser(@PathVariable("realm") String realm, @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String authorization);

    @DeleteMapping("/admin/realms/{realm}/users/{userId}/groups/{groupId}")
    void removeUserFromGroup(@PathVariable("realm") String realm, @PathVariable("userId") String userId,
            @PathVariable("groupId") String groupId, @RequestHeader("Authorization") String authorization);
}
