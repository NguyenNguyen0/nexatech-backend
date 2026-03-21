package com.nexatech.identity.service.membership;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nexatech.identity.client.keycloak.membership.KeycloakGroupUserClient;
import com.nexatech.identity.service.support.KeycloakAdminTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserGroupMembershipService {

    private final KeycloakGroupUserClient keycloakGroupUserClient;
    private final KeycloakAdminTokenProvider adminTokenProvider;

    @Value("${keycloak.realm}")
    private String realm;

    public void addUserToGroup(String userId, String groupId) {
        keycloakGroupUserClient.addUserToGroup(realm, userId, groupId, adminTokenProvider.adminAuthorizationHeader());
    }

    public List<Map<String, Object>> getGroupsForUser(String userId) {
        return keycloakGroupUserClient.getGroupsForUser(realm, userId, adminTokenProvider.adminAuthorizationHeader());
    }

    public void removeUserFromGroup(String userId, String groupId) {
        keycloakGroupUserClient.removeUserFromGroup(realm, userId, groupId, adminTokenProvider.adminAuthorizationHeader());
    }
}
