package com.nexatech.user.config;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.nexatech.user.client.IdentityIamClient;
import com.nexatech.user.constant.PermissionsAccept;
import com.nexatech.user.dto.request.RoleCreateRequest;
import com.nexatech.user.dto.request.RoleRefRequest;
import com.nexatech.user.dto.base.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequiredPermissionsInitializer implements ApplicationRunner {

    private static final String ADMIN_ROLE_NAME = "ADMIN";

    private final IdentityIamClient identityIamClient;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Wait briefly to allow identity-service to be fully up if starting simultaneously
            Thread.sleep(5000);
            
            // Resolve existing roles/permissions from identity-service
            ApiResponse<List<Map<String, Object>>> response = identityIamClient.listRoles();
            if (response == null || response.getResult() == null) {
                log.warn("Failed to fetch existing roles from identity-service. Skipping permission initialization.");
                return;
            }

            Set<String> existingRoleNamesUpper = response.getResult().stream()
                    .map(r -> r.get("name"))
                    .filter(n -> n != null)
                    .map(Object::toString)
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet());

            List<String> allPermissions = new ArrayList<>();
            allPermissions.addAll(PermissionsAccept.allCodes());

            for (String permissionName : allPermissions) {
                ensurePermissionExists(permissionName, existingRoleNamesUpper);
            }

            ensureAdminHasAllPermissions(allPermissions);
        } catch (Exception e) {
            log.error("Could not initialize permissions in Keycloak. Identity service might not be reachable yet: {}", e.getMessage());
        }
    }

    private void ensurePermissionExists(String permissionName, Set<String> existingRoleNamesUpper) {
        String expectedUpper = permissionName != null ? permissionName.trim().toUpperCase(Locale.ROOT) : null;
        if (expectedUpper == null || expectedUpper.isEmpty()) {
            return;
        }

        if (existingRoleNamesUpper.contains(expectedUpper)) {
            log.info("Keycloak permission exists: {}", expectedUpper);
            return;
        }

        log.warn("Keycloak permission missing, creating permission={}...", expectedUpper);
        try {
            identityIamClient.createRole(RoleCreateRequest.builder()
                    .name(expectedUpper)
                    .description(generatePermissionDescription(expectedUpper))
                    .build());
            existingRoleNamesUpper.add(expectedUpper);
        } catch (Exception e) {
            log.error("Failed to create permission {}: {}", expectedUpper, e.getMessage());
        }
    }

    private void ensureAdminHasAllPermissions(List<String> permissionNames) {
        if (!roleExists(ADMIN_ROLE_NAME)) {
            log.warn("Keycloak role {} does not exist. Skipping ADMIN permission synchronization.", ADMIN_ROLE_NAME);
            return;
        }

        try {
            ApiResponse<List<Map<String, Object>>> response = identityIamClient.getCompositeRoles(ADMIN_ROLE_NAME);
            List<Map<String, Object>> assignedRoles = response != null && response.getResult() != null
                    ? response.getResult()
                    : List.of();

            Set<String> assignedRoleNamesUpper = assignedRoles.stream()
                    .map(r -> r.get("name"))
                    .filter(n -> n != null)
                    .map(Object::toString)
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet());

            List<RoleRefRequest> missingPermissions = permissionNames.stream()
                    .map(this::normalizeRoleName)
                    .filter(name -> name != null && !assignedRoleNamesUpper.contains(name))
                    .map(this::resolveRoleRef)
                    .filter(ref -> ref != null)
                    .toList();

            if (missingPermissions.isEmpty()) {
                log.info("Keycloak role {} already has all required permissions.", ADMIN_ROLE_NAME);
                return;
            }

            identityIamClient.addCompositeRoles(ADMIN_ROLE_NAME, missingPermissions);
            log.warn("Assigned {} missing permissions to Keycloak role {}.", missingPermissions.size(), ADMIN_ROLE_NAME);
        } catch (Exception e) {
            log.error("Failed to synchronize permissions for role {}: {}", ADMIN_ROLE_NAME, e.getMessage());
        }
    }

    private boolean roleExists(String roleName) {
        try {
            ApiResponse<Map<String, Object>> response = identityIamClient.getRoleByName(roleName);
            Map<String, Object> role = response != null ? response.getResult() : null;
            return role != null && role.get("name") != null;
        } catch (Exception e) {
            log.warn("Could not resolve Keycloak role {}: {}", roleName, e.getMessage());
            return false;
        }
    }

    private RoleRefRequest resolveRoleRef(String roleName) {
        try {
            ApiResponse<Map<String, Object>> response = identityIamClient.getRoleByName(roleName);
            Map<String, Object> role = response != null ? response.getResult() : null;
            if (role == null || role.get("name") == null) {
                log.warn("Keycloak permission {} was not found while syncing ADMIN composites.", roleName);
                return null;
            }

            Object roleIdObj = role.get("id");
            return RoleRefRequest.builder()
                    .id(roleIdObj != null ? roleIdObj.toString() : null)
                    .name(role.get("name").toString())
                    .build();
        } catch (Exception e) {
            log.error("Could not resolve Keycloak permission {}: {}", roleName, e.getMessage());
            return null;
        }
    }

    private String normalizeRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return null;
        }
        return roleName.trim().toUpperCase(Locale.ROOT);
    }

    private String generatePermissionDescription(String permissionName) {
        if (permissionName == null) return "System generated permission";
        
        String[] parts = permissionName.split("_");
        if (parts.length >= 2) {
            String module = parts[0].toLowerCase(Locale.ROOT);
            String action = parts[1].toLowerCase(Locale.ROOT);
            
            boolean isDeny = permissionName.endsWith("_DENY");
            String baseDesc = "Permission to " + action + " " + module;
            
            return isDeny ? "Deny " + baseDesc : baseDesc;
        }
        return "System generated permission for " + permissionName;
    }
}
