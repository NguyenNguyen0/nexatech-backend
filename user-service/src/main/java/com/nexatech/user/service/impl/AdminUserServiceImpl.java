package com.nexatech.user.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexatech.user.client.IdentityIamClient;
import com.nexatech.user.dto.event.KeycloakCredentialPayload;
import com.nexatech.user.dto.event.KeycloakUserProvisionPayload;
import com.nexatech.user.dto.request.AdminUserCreateRequest;
import com.nexatech.user.dto.request.RoleRefRequest;
import com.nexatech.user.dto.request.UserCreationRequest;
import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.response.UserResponse;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;
import com.nexatech.user.mapper.UserMapper;
import com.nexatech.user.model.User;
import com.nexatech.user.repository.UserRepository;
import com.nexatech.user.service.AdminUserService;
import com.nexatech.user.service.IdentityResilienceService;
import com.nexatech.user.service.IdentityServiceDependencyGuard;
import com.nexatech.user.service.audit.AuditEventPublisher;

import feign.FeignException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserServiceImpl implements AdminUserService {

    UserRepository userRepository;
    UserMapper userMapper;
    IdentityResilienceService identityResilienceService;
    IdentityServiceDependencyGuard identityServiceDependencyGuard;
    IdentityIamClient identityIamClient;
    AuditEventPublisher auditEventPublisher;

    @Override
    @Transactional
    public UserResponse createUserWithKeycloakProvision(AdminUserCreateRequest request) {
        identityServiceDependencyGuard.assertIdentityServiceReady();
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new AppException(ErrorCode.USERNAME_IS_MISSING);
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_EMAIL_EXISTS);
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        String requestedId = request.getId() != null && !request.getId().isBlank() ? request.getId() : null;

        boolean enabled = request.getEnabled() != null ? request.getEnabled() : true;
        boolean emailVerified = Boolean.TRUE.equals(request.getEmailVerified());
        boolean temporaryPassword = Boolean.TRUE.equals(request.getTemporaryPassword());

        KeycloakUserProvisionPayload provision = KeycloakUserProvisionPayload.builder()
                .id(requestedId)
                .username(request.getUsername())
                .enabled(enabled)
                .email(request.getEmail())
                .emailVerified(emailVerified)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .credentials(List.of(KeycloakCredentialPayload.builder()
                        .type("password")
                        .value(request.getPassword())
                        .temporary(temporaryPassword)
                        .build()))
                .build();

        ApiResponse<String> created;
        try {
            created = identityResilienceService.createKeycloakUser(provision);
        } catch (Exception ex) {
            if (ex instanceof AppException appException && appException.getErrorCode() == ErrorCode.DEPENDENCY_UNAVAILABLE) {
                throw appException;
            }
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        }
        String keycloakUserId = created != null ? created.getResult() : null;
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        }

        String assignedRole = syncUserRoleOnCreate(keycloakUserId, request.getRole());

        UserCreationRequest profile = UserCreationRequest.builder()
                .id(keycloakUserId)
                .email(request.getEmail())
                .username(request.getUsername())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .avatarUrl(request.getAvatarUrl())
                .phone(request.getPhone())
                .dob(request.getDob())
                .gender(request.getGender())
                .onBoarded(request.getOnBoarded() != null ? request.getOnBoarded() : false)
                .build();

        User user = userMapper.toUser(profile);
        user.setId(keycloakUserId);
        User saved = userRepository.save(user);
        UserResponse response = userMapper.toResponse(saved);
        response.setRole(assignedRole);

        auditEventPublisher.publish(com.nexatech.user.dto.event.AuditLogEvent.builder()
                .action("USER_CREATED")
                .entityType("User")
                .entityId(saved.getId())
                .entityLabel(saved.getUsername())
                .newValue("{\"username\":\"" + escape(saved.getUsername())
                        + "\",\"email\":\"" + escape(saved.getEmail())
                        + "\",\"role\":\"" + escape(assignedRole) + "\"}")
                .message("Admin created user account (role: " + (assignedRole != null ? assignedRole : "—") + ")")
                .build());

        return response;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    @Override
    @Transactional
    public void updateUserRole(String userId, String roleName) {
        validateUserExists(userId);
        String previousRole = getPrimaryRole(userId);
        replaceAllRealmRoles(userId, roleName);

        String username = userRepository.findById(userId).map(User::getUsername).orElse(null);
        String normalized = normalizeRole(roleName);
        auditEventPublisher.publish(com.nexatech.user.dto.event.AuditLogEvent.builder()
                .action("USER_ROLE_CHANGED")
                .entityType("User")
                .entityId(userId)
                .entityLabel(username)
                .oldValue(previousRole)
                .newValue(normalized)
                .message("Changed user role from " + (previousRole != null ? previousRole : "—")
                        + " to " + (normalized != null ? normalized : "—"))
                .build());
    }

    private String getPrimaryRole(String userId) {
        try {
            List<RoleRefRequest> roles = getCurrentRealmRoles(userId);
            return roles.stream()
                    .map(RoleRefRequest::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .filter(name -> !name.startsWith("default-roles-"))
                    .map(name -> name.toUpperCase(Locale.ROOT))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    @Transactional
    public void removeUserRole(String userId, String roleName) {
        validateUserExists(userId);
        String normalized = normalizeRole(roleName);
        if (normalized == null) {
            throw new AppException(ErrorCode.ROLE_NAME_REQUIRED);
        }
        try {
            List<RoleRefRequest> currentRoles = getCurrentRealmRoles(userId);
            if (currentRoles.isEmpty()) {
                throw new AppException(ErrorCode.ROLE_NOT_FOUND);
            }
            boolean assigned = currentRoles.stream()
                    .map(RoleRefRequest::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .map(name -> name.trim().toUpperCase(Locale.ROOT))
                    .anyMatch(normalized::equals);
            if (!assigned) {
                throw new AppException(ErrorCode.ROLE_NOT_FOUND);
            }
            RoleRefRequest ref = resolveRoleRef(normalized);
            identityResilienceService.removeRealmRolesFromUser(userId, List.of(ref));

            String username = userRepository.findById(userId).map(User::getUsername).orElse(null);
            auditEventPublisher.publish(com.nexatech.user.dto.event.AuditLogEvent.builder()
                    .action("USER_ROLE_REVOKED")
                    .entityType("User")
                    .entityId(userId)
                    .entityLabel(username)
                    .oldValue(normalized)
                    .message("Revoked role " + normalized + " from user")
                    .build());
        } catch (FeignException ex) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        }
    }

    private String syncUserRoleOnCreate(String userId, String roleName) {
        String normalized = normalizeRole(roleName);
        if (normalized == null) {
            return null;
        }
        try {
            RoleRefRequest ref = resolveRoleRef(normalized);
            identityResilienceService.assignRealmRolesToUser(userId, List.of(ref));
            return ref.getName() != null ? ref.getName().toUpperCase(Locale.ROOT) : normalized;
        } catch (FeignException ex) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        }
    }

    private void replaceAllRealmRoles(String userId, String roleName) {
        try {
            List<RoleRefRequest> currentRoles = getCurrentRealmRoles(userId);
            if (!currentRoles.isEmpty()) {
                identityResilienceService.removeRealmRolesFromUser(userId, currentRoles);
            }

            String normalized = normalizeRole(roleName);
            if (normalized == null) {
                return;
            }
            RoleRefRequest ref = resolveRoleRef(normalized);
            identityResilienceService.assignRealmRolesToUser(userId, List.of(ref));
        } catch (FeignException ex) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        }
    }

    private List<RoleRefRequest> getCurrentRealmRoles(String userId) {
        ApiResponse<Map<String, Object>> response = identityResilienceService.getUserRoleMappings(userId);
        Map<String, Object> payload = response != null ? response.getResult() : null;
        if (payload == null) {
            return List.of();
        }
        Object realmMappingsObj = payload.get("realmMappings");
        if (!(realmMappingsObj instanceof List<?> realmMappings)) {
            return List.of();
        }
        List<RoleRefRequest> refs = new ArrayList<>();
        for (Object item : realmMappings) {
            if (!(item instanceof Map<?, ?> roleMap)) {
                continue;
            }
            Object idObj = roleMap.get("id");
            Object nameObj = roleMap.get("name");
            if (nameObj == null) {
                continue;
            }
            refs.add(RoleRefRequest.builder()
                    .id(idObj != null ? idObj.toString() : null)
                    .name(nameObj.toString())
                    .build());
        }
        return refs;
    }

    private RoleRefRequest resolveRoleRef(String roleName) {
        var roleResponse = identityIamClient.getRoleByName(roleName);
        Map<String, Object> role = roleResponse != null ? roleResponse.getResult() : null;
        if (role == null || role.get("name") == null) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        }
        Object roleIdObj = role.get("id");
        return RoleRefRequest.builder()
                .id(roleIdObj != null ? roleIdObj.toString() : null)
                .name(role.get("name").toString())
                .build();
    }

    private void validateUserExists(String userId) {
        userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private static String normalizeRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return null;
        }
        return roleName.trim().toUpperCase(Locale.ROOT);
    }
}
