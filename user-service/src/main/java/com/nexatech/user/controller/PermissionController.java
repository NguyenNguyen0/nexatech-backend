package com.nexatech.user.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.user.client.IdentityIamClient;
import com.nexatech.user.constant.PermissionsAccept;
import com.nexatech.user.constant.PermissionsDeny;
import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.request.RoleCreateRequest;
import com.nexatech.user.dto.response.RoleSummaryResponse;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PermissionController {

    private static final String DENY_SUFFIX = "_DENY";

    IdentityIamClient identityIamClient;

    @PostMapping
    // @RequiresPermission(PermissionsAccept.Definition.WRITE)
    public ApiResponse<Void> createPermission(@Valid @RequestBody RoleCreateRequest request) {
        identityIamClient.createRole(request);
        return ApiResponse.<Void>builder().build();
    }

    /**
     * Catalog of grant permission codes (static list for admin UI), same shape as Keycloak role summaries.
     */
    @GetMapping("/accept")
    // @RequiresPermission(PermissionsAccept.Definition.READ)
    public ApiResponse<List<RoleSummaryResponse>> listPermissionAcceptCatalog() {
        return ApiResponse.<List<RoleSummaryResponse>>builder()
                .result(toCatalogRoleSummaries(PermissionsAccept.allCodes()))
                .build();
    }

    /**
     * Catalog of deny permission codes ({@code *_DENY}, static list for admin UI).
     */
    @GetMapping("/deny")
    // @RequiresPermission(PermissionsAccept.Definition.READ)
    public ApiResponse<List<RoleSummaryResponse>> listPermissionDenyCatalog() {
        return ApiResponse.<List<RoleSummaryResponse>>builder()
                .result(toCatalogRoleSummaries(PermissionsDeny.allCodes()))
                .build();
    }

    /**
     * Grant-style realm roles from Keycloak in real time (names not ending with {@code _DENY}).
     */
    @GetMapping("/accept/keycloak")
    // @RequiresPermission(PermissionsAccept.Definition.READ)
    public ApiResponse<List<RoleSummaryResponse>> listPermissionAcceptFromKeycloak() {
        return ApiResponse.<List<RoleSummaryResponse>>builder()
                .result(filterRealmRolesByDenySuffix(false))
                .build();
    }

    /**
     * Deny realm roles from Keycloak in real time ({@code *_DENY}).
     */
    @GetMapping("/deny/keycloak")
    // @RequiresPermission(PermissionsAccept.Definition.READ)
    public ApiResponse<List<RoleSummaryResponse>> listPermissionDenyFromKeycloak() {
        return ApiResponse.<List<RoleSummaryResponse>>builder()
                .result(filterRealmRolesByDenySuffix(true))
                .build();
    }

    /** Lists all realm roles from Keycloak (not limited to the static catalog). */
    @GetMapping
    // @RequiresPermission(PermissionsAccept.Definition.READ)
    public ApiResponse<List<RoleSummaryResponse>> listPermissions() {
        List<RoleSummaryResponse> permissions = identityIamClient.listRoles().getResult()
                .stream()
                .map(this::toRoleSummary)
                .toList();
        return ApiResponse.<List<RoleSummaryResponse>>builder().result(permissions).build();
    }

    @GetMapping("/{permissionName}")
    // @RequiresPermission(PermissionsAccept.Definition.READ)
    public ApiResponse<RoleSummaryResponse> getPermissionByName(@PathVariable String permissionName) {
        return ApiResponse.<RoleSummaryResponse>builder()
                .result(toRoleSummary(identityIamClient.getRoleByName(permissionName).getResult()))
                .build();
    }

    @DeleteMapping("/{permissionName}")
    // @RequiresPermission(PermissionsAccept.Definition.WRITE)
    public ApiResponse<Void> deletePermission(@PathVariable String permissionName) {
        identityIamClient.deleteRole(permissionName);
        return ApiResponse.<Void>builder().build();
    }

    private RoleSummaryResponse toRoleSummary(Map<String, Object> source) {
        if (source == null) {
            return new RoleSummaryResponse();
        }
        return RoleSummaryResponse.builder()
                .id(getString(source.get("id")))
                .name(getString(source.get("name")))
                .description(getString(source.get("description")))
                .build();
    }

    private String getString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Keycloak-shaped items; {@code id} is a new random UUID on each request (catalog is not persisted).
     */
    private List<RoleSummaryResponse> toCatalogRoleSummaries(List<String> codes) {
        return codes.stream()
                .map(code -> RoleSummaryResponse.builder()
                        .id(UUID.randomUUID().toString())
                        .name(code)
                        .description("")
                        .build())
                .toList();
    }

    private List<RoleSummaryResponse> filterRealmRolesByDenySuffix(boolean denyOnly) {
        List<String> validCodes = denyOnly ? PermissionsDeny.allCodes() : PermissionsAccept.allCodes();
        return identityIamClient.listRoles().getResult().stream()
                .map(this::toRoleSummary)
                .filter(r -> {
                    String n = r.getName();
                    return n != null && validCodes.contains(n);
                })
                .sorted(Comparator.comparing(RoleSummaryResponse::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }
}
