package com.nexatech.identity.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nexatech.identity.client.keycloak.KeycloakRoleClient;
import com.nexatech.identity.dto.request.KeycloakRoleRef;
import com.nexatech.identity.dto.request.RoleCreateParam;
import com.nexatech.identity.service.support.KeycloakAdminTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleService {
    private static final Set<String> EXCLUDED_SYSTEM_ROLE_NAMES = Set.of(
            "uma_authorization",
            "offline_access");

    private final KeycloakRoleClient keycloakRoleClient;
    private final KeycloakAdminTokenProvider adminTokenProvider;

    @Value("${openfeign.keycloak.realm}")
    private String realm;

    public void createRole(RoleCreateParam param) {
        if (param == null || param.getName() == null || param.getName().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        keycloakRoleClient.createRole(realm, adminTokenProvider.adminAuthorizationHeader(), param);
    }

    public List<Map<String, Object>> listRoles() {
        return keycloakRoleClient.listRoles(realm, adminTokenProvider.adminAuthorizationHeader())
                .stream()
                .filter(this::isNotSystemRole)
                .toList();
    }

    public Map<String, Object> getRoleByName(String roleName) {
        return keycloakRoleClient.getRoleByName(realm, roleName, adminTokenProvider.adminAuthorizationHeader());
    }

    public void addCompositeRoles(String roleName, List<KeycloakRoleRef> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("roles body is required");
        }
        keycloakRoleClient.addCompositeRoles(realm, roleName, adminTokenProvider.adminAuthorizationHeader(), roles);
    }

    public List<Map<String, Object>> getCompositeRoles(String roleName) {
        return keycloakRoleClient.getCompositeRoles(realm, roleName, adminTokenProvider.adminAuthorizationHeader());
    }

    public void removeCompositeRoles(String roleName, List<KeycloakRoleRef> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("roles body is required");
        }
        keycloakRoleClient.removeCompositeRoles(realm, roleName, adminTokenProvider.adminAuthorizationHeader(), roles);
    }

    public Map<String, Object> getRoleById(String roleId) {
        return keycloakRoleClient.getRoleById(realm, roleId, adminTokenProvider.adminAuthorizationHeader());
    }

    public void deleteRole(String roleName) {
        keycloakRoleClient.deleteRole(realm, roleName, adminTokenProvider.adminAuthorizationHeader());
    }

    private boolean isNotSystemRole(Map<String, Object> role) {
        Object rawName = role.get("name");
        if (rawName == null) {
            return true;
        }
        String roleName = rawName.toString();
        return !EXCLUDED_SYSTEM_ROLE_NAMES.contains(roleName) && !roleName.startsWith("default-roles-");
    }
}
