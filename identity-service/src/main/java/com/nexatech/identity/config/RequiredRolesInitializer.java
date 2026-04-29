package com.nexatech.identity.config;

import java.util.Locale;
import java.util.Set;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.nexatech.identity.dto.request.RoleCreateParam;
import com.nexatech.identity.service.RoleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequiredRolesInitializer implements ApplicationRunner {

    private final RoleService roleService;

    // Business roles that should always exist in Keycloak realm.
    // These are realm-level (not composite) roles.
    private static final List<String> REQUIRED_ROLES = List.of(
            "CUSTOMER",
            "ADMIN",
            "MANAGER",
            "STAFF",
            "WAREHOUSE",
            "ACCOUNTANT");

    @Override
    public void run(ApplicationArguments args) {
        // Resolve existing roles once to avoid race/duplication due to casing differences.
        Set<String> existingRoleNamesUpper = roleService.listRoles().stream()
                .map(r -> r.get("name"))
                .filter(n -> n != null)
                .map(Object::toString)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        for (String roleName : REQUIRED_ROLES) {
            ensureRoleExists(roleName, existingRoleNamesUpper);
        }
    }

    private void ensureRoleExists(String roleName, Set<String> existingRoleNamesUpper) {
        String expectedUpper = roleName != null ? roleName.trim().toUpperCase(Locale.ROOT) : null;
        if (expectedUpper == null || expectedUpper.isEmpty()) {
            return;
        }

        if (existingRoleNamesUpper.contains(expectedUpper)) {
            log.info("Keycloak role exists: {}", expectedUpper);
            return;
        }

        log.warn("Keycloak role missing, creating role={}...", expectedUpper);
        roleService.createRole(RoleCreateParam.builder()
                .name(expectedUpper)
                .description("Created by system")
                .build());
    }
}

