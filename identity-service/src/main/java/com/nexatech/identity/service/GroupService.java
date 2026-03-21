package com.nexatech.identity.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nexatech.identity.client.keycloak.KeycloakGroupClient;
import com.nexatech.identity.dto.request.GroupCreateParam;
import com.nexatech.identity.dto.request.KeycloakRoleRef;
import com.nexatech.identity.service.support.KeycloakAdminTokenProvider;
import com.nexatech.identity.service.support.KeycloakResponseUtils;

import feign.Response;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final KeycloakGroupClient keycloakGroupClient;
    private final KeycloakAdminTokenProvider adminTokenProvider;

    @Value("${openfeign.keycloak.realm}")
    private String realm;

    public String createGroup(GroupCreateParam param) {
        if (param == null || param.getName() == null || param.getName().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        try (Response response = keycloakGroupClient.createGroup(realm, adminTokenProvider.adminAuthorizationHeader(), param)) {
            return KeycloakResponseUtils.idFromCreatedResourceLocation(response, 201);
        }
    }

    public List<Map<String, Object>> listGroups(String search) {
        return keycloakGroupClient.listGroups(realm, adminTokenProvider.adminAuthorizationHeader(), search);
    }

    public Map<String, Object> getGroupRoleMappings(String groupId) {
        return keycloakGroupClient.getGroupRoleMappings(realm, groupId, adminTokenProvider.adminAuthorizationHeader());
    }

    public void assignRealmRolesToGroup(String groupId, List<KeycloakRoleRef> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("roles body is required");
        }
        keycloakGroupClient.assignRealmRolesToGroup(realm, groupId, adminTokenProvider.adminAuthorizationHeader(), roles);
    }

    public void removeRealmRolesFromGroup(String groupId, List<KeycloakRoleRef> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("roles body is required");
        }
        keycloakGroupClient.removeRealmRolesFromGroup(realm, groupId, adminTokenProvider.adminAuthorizationHeader(), roles);
    }

    public List<Map<String, Object>> getGroupMembers(String groupId) {
        return keycloakGroupClient.getGroupMembers(realm, groupId, adminTokenProvider.adminAuthorizationHeader());
    }

    public void deleteGroup(String groupId) {
        keycloakGroupClient.deleteGroup(realm, groupId, adminTokenProvider.adminAuthorizationHeader());
    }
}
