package com.nexatech.identity.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nexatech.identity.client.KeycloakSessionClient;
import com.nexatech.identity.client.KeycloakUserClient;
import com.nexatech.identity.dto.response.SessionResponse;
import com.nexatech.identity.service.support.KeycloakAdminTokenProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final KeycloakUserClient keycloakUserClient;
    private final KeycloakSessionClient keycloakSessionClient;
    private final KeycloakAdminTokenProvider adminTokenProvider;

    @Value("${openfeign.keycloak.realm}")
    private String realm;

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    public List<SessionResponse> getMySessions(String userId, String currentSessionId) {
        String token = adminTokenProvider.adminAuthorizationHeader();
        List<Map<String, Object>> raw = keycloakUserClient.getSessions(realm, userId, token);
        if (raw == null) {
            return Collections.emptyList();
        }
        return raw.stream()
                .map(s -> mapToSessionResponse(s, currentSessionId, "REGULAR"))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getUserSessionsRaw(String userId) {
        return keycloakUserClient.getSessions(realm, userId, adminTokenProvider.adminAuthorizationHeader());
    }

    public void deleteSession(String sessionId, Boolean isOffline) {
        keycloakSessionClient.deleteSession(realm, sessionId, adminTokenProvider.adminAuthorizationHeader(), isOffline);
    }

    public void logoutUser(String userId) {
        keycloakUserClient.logoutUser(realm, userId, adminTokenProvider.adminAuthorizationHeader());
    }

    public Map<String, Object> logoutAll() {
        return keycloakSessionClient.logoutAll(realm, adminTokenProvider.adminAuthorizationHeader());
    }

    public List<Map<String, Object>> getClientUserSessions(String clientUuid, Integer first, Integer max) {
        return keycloakSessionClient.getClientUserSessions(realm, clientUuid,
                adminTokenProvider.adminAuthorizationHeader(), first, max);
    }

    public Map<String, Object> getClientSessionCount(String clientUuid) {
        return keycloakSessionClient.getClientSessionCount(realm, clientUuid,
                adminTokenProvider.adminAuthorizationHeader());
    }

    public List<Map<String, Object>> getClientSessionStats() {
        return keycloakSessionClient.getClientSessionStats(realm, adminTokenProvider.adminAuthorizationHeader());
    }

    public List<Map<String, Object>> getClientOfflineSessions(String clientUuid, Integer first, Integer max) {
        return keycloakSessionClient.getClientOfflineSessions(realm, clientUuid,
                adminTokenProvider.adminAuthorizationHeader(), first, max);
    }

    public Map<String, Object> getClientOfflineSessionCount(String clientUuid) {
        return keycloakSessionClient.getClientOfflineSessionCount(realm, clientUuid,
                adminTokenProvider.adminAuthorizationHeader());
    }

    public List<Map<String, Object>> getUserOfflineSessions(String userId, String clientUuid) {
        return keycloakUserClient.getOfflineSessions(realm, userId, clientUuid,
                adminTokenProvider.adminAuthorizationHeader());
    }

    @SuppressWarnings("unchecked")
    private SessionResponse mapToSessionResponse(Map<String, Object> raw, String currentSessionId, String type) {
        String id = asString(raw.get("id"));
        String started = toIso(raw.get("start"));
        String lastAccess = toIso(raw.get("lastAccess"));
        String ipAddress = asString(raw.get("ipAddress"));
        String username = asString(raw.get("username"));
        String userId = asString(raw.get("userId"));
        boolean rememberMe = Boolean.TRUE.equals(raw.get("rememberMe"));

        List<String> clients = new ArrayList<>();
        Object clientsObj = raw.get("clients");
        if (clientsObj instanceof Map) {
            clients.addAll(((Map<String, String>) clientsObj).values());
        }

        boolean current = id != null && id.equals(currentSessionId);

        return SessionResponse.builder()
                .id(id)
                .userId(userId)
                .username(username)
                .type(type)
                .started(started)
                .lastAccess(lastAccess)
                .ipAddress(ipAddress)
                .clients(clients)
                .current(current)
                .rememberMe(rememberMe)
                .build();
    }

    private String toIso(Object epochMillisObj) {
        if (epochMillisObj == null) return null;
        try {
            long millis = ((Number) epochMillisObj).longValue();
            return ISO_FORMATTER.format(Instant.ofEpochMilli(millis));
        } catch (Exception e) {
            return epochMillisObj.toString();
        }
    }

    private String asString(Object obj) {
        return obj != null ? obj.toString() : null;
    }
}
