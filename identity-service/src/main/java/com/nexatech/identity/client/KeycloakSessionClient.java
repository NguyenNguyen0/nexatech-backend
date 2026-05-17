package com.nexatech.identity.client;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "keycloakSession", contextId = "keycloakSession", url = "${openfeign.keycloak.url}")
public interface KeycloakSessionClient {

    @DeleteMapping("/admin/realms/{realm}/sessions/{sessionId}")
    void deleteSession(@PathVariable("realm") String realm,
            @PathVariable("sessionId") String sessionId,
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "isOffline", required = false) Boolean isOffline);

    @PostMapping("/admin/realms/{realm}/logout-all")
    Map<String, Object> logoutAll(@PathVariable("realm") String realm,
            @RequestHeader("Authorization") String authorization);

    @GetMapping("/admin/realms/{realm}/clients/{clientUuid}/user-sessions")
    List<Map<String, Object>> getClientUserSessions(@PathVariable("realm") String realm,
            @PathVariable("clientUuid") String clientUuid,
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "first", required = false) Integer first,
            @RequestParam(value = "max", required = false) Integer max);

    @GetMapping("/admin/realms/{realm}/clients/{clientUuid}/session-count")
    Map<String, Object> getClientSessionCount(@PathVariable("realm") String realm,
            @PathVariable("clientUuid") String clientUuid,
            @RequestHeader("Authorization") String authorization);

    @GetMapping("/admin/realms/{realm}/client-session-stats")
    List<Map<String, Object>> getClientSessionStats(@PathVariable("realm") String realm,
            @RequestHeader("Authorization") String authorization);

    @GetMapping("/admin/realms/{realm}/clients/{clientUuid}/offline-sessions")
    List<Map<String, Object>> getClientOfflineSessions(@PathVariable("realm") String realm,
            @PathVariable("clientUuid") String clientUuid,
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "first", required = false) Integer first,
            @RequestParam(value = "max", required = false) Integer max);

    @GetMapping("/admin/realms/{realm}/clients/{clientUuid}/offline-session-count")
    Map<String, Object> getClientOfflineSessionCount(@PathVariable("realm") String realm,
            @PathVariable("clientUuid") String clientUuid,
            @RequestHeader("Authorization") String authorization);
}
