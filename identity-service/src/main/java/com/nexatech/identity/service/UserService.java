package com.nexatech.identity.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nexatech.identity.client.keycloak.KeycloakRoleClient;
import com.nexatech.identity.client.keycloak.KeycloakUserClient;
import com.nexatech.identity.dto.request.Credential;
import com.nexatech.identity.dto.request.KeycloakRoleRef;
import com.nexatech.identity.dto.request.UserCreationParam;
import com.nexatech.identity.dto.request.UserUpdateParam;
import com.nexatech.identity.service.support.KeycloakAdminTokenProvider;
import com.nexatech.identity.service.support.KeycloakResponseUtils;

import feign.Response;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final KeycloakUserClient keycloakUserClient;
    private final KeycloakRoleClient keycloakRoleClient;
    private final KeycloakAdminTokenProvider adminTokenProvider;

    @Value("${openfeign.keycloak.realm}")
    private String realm;

    public String createUser(UserCreationParam param) {
        if (param == null || param.getCredentials() == null || param.getCredentials().isEmpty()) {
            throw new IllegalArgumentException("credentials is required");
        }

        UserCreationParam body = UserCreationParam.builder()
                .username(param.getUsername())
                .firstName(param.getFirstName())
                .lastName(param.getLastName())
                .email(param.getEmail())
                .enabled(param.isEnabled())
                .emailVerified(param.isEmailVerified())
                .credentials(param.getCredentials())
                .build();

        try (Response response = keycloakUserClient.createUser(realm, adminTokenProvider.adminAuthorizationHeader(), body)) {
            return KeycloakResponseUtils.idFromCreatedResourceLocation(response, 201);
        }
    }

    public List<Map<String, Object>> listUsers(String username) {
        return keycloakUserClient.listUsers(realm, adminTokenProvider.adminAuthorizationHeader(), username);
    }

    public Map<String, Object> getUserById(String userId) {
        return keycloakUserClient.getUserById(realm, userId, adminTokenProvider.adminAuthorizationHeader());
    }

    public void updateUser(String userId, UserUpdateParam param) {
        keycloakUserClient.updateUser(realm, userId, adminTokenProvider.adminAuthorizationHeader(),
                UserUpdateParam.builder()
                        .username(param.getUsername())
                        .firstName(param.getFirstName())
                        .lastName(param.getLastName())
                        .email(param.getEmail())
                        .enabled(param.isEnabled())
                        .emailVerified(param.isEmailVerified())
                        .build());
    }

    public void deleteUser(String userId) {
        keycloakUserClient.deleteUser(realm, userId, adminTokenProvider.adminAuthorizationHeader());
    }

    public List<Map<String, Object>> getCredentials(String userId) {
        return keycloakUserClient.getCredentials(realm, userId, adminTokenProvider.adminAuthorizationHeader());
    }

    public void resetPassword(String userId, Credential credential) {
        keycloakUserClient.resetPassword(realm, userId, adminTokenProvider.adminAuthorizationHeader(), credential);
    }

    public Map<String, Object> getRoleMappings(String userId) {
        return keycloakUserClient.getRoleMappings(realm, userId, adminTokenProvider.adminAuthorizationHeader());
    }

    public List<Map<String, Object>> getSessions(String userId) {
        return keycloakUserClient.getSessions(realm, userId, adminTokenProvider.adminAuthorizationHeader());
    }

    public void assignRealmRolesToUser(String userId, List<KeycloakRoleRef> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("roles body is required");
        }
        keycloakRoleClient.assignRealmRolesToUser(realm, userId, adminTokenProvider.adminAuthorizationHeader(), roles);
    }

    public void removeRealmRolesFromUser(String userId, List<KeycloakRoleRef> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("roles body is required");
        }
        keycloakRoleClient.removeRealmRolesFromUser(realm, userId, adminTokenProvider.adminAuthorizationHeader(), roles);
    }
}
