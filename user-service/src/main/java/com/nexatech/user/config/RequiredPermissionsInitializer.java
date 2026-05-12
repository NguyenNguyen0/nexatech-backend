package com.nexatech.user.config;

import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.nexatech.user.client.IdentityIamClient;
import com.nexatech.user.constant.PermissionsAccept;
import com.nexatech.user.constant.PermissionsDeny;
import com.nexatech.user.dto.request.RoleCreateRequest;
import com.nexatech.user.dto.base.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequiredPermissionsInitializer implements ApplicationRunner {

    private final IdentityIamClient identityIamClient;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Wait briefly to allow identity-service to be fully up if starting simultaneously
            Thread.sleep(5000);
            
            // Resolve existing roles/permissions from identity-service
            ApiResponse<List<java.util.Map<String, Object>>> response = identityIamClient.listRoles();
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
            allPermissions.addAll(PermissionsDeny.allCodes());

            for (String permissionName : allPermissions) {
                ensurePermissionExists(permissionName, existingRoleNamesUpper);
            }
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
        } catch (Exception e) {
            log.error("Failed to create permission {}: {}", expectedUpper, e.getMessage());
        }
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
