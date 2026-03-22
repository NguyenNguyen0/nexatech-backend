package com.nexatech.user.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.user.client.IdentityIamClient;
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

    IdentityIamClient identityIamClient;

    @PostMapping
    public ApiResponse<Void> createPermission(@Valid @RequestBody RoleCreateRequest request) {
        identityIamClient.createRole(request);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping
    public ApiResponse<List<RoleSummaryResponse>> listPermissions() {
        List<RoleSummaryResponse> permissions = identityIamClient.listRoles().getResult()
                .stream()
                .map(this::toRoleSummary)
                .toList();
        return ApiResponse.<List<RoleSummaryResponse>>builder().result(permissions).build();
    }

    @GetMapping("/{permissionName}")
    public ApiResponse<RoleSummaryResponse> getPermissionByName(@PathVariable String permissionName) {
        return ApiResponse.<RoleSummaryResponse>builder()
                .result(toRoleSummary(identityIamClient.getRoleByName(permissionName).getResult()))
                .build();
    }

    @DeleteMapping("/{permissionName}")
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
}

