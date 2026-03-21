package com.nexatech.user.controller;

import java.util.List;
import java.util.Map;

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
import com.nexatech.user.dto.request.RoleRefRequest;
import com.nexatech.user.dto.response.RoleSummaryResponse;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoleController {

    IdentityIamClient identityIamClient;

    @PostMapping
    public ApiResponse<Void> createRole(@RequestBody RoleCreateRequest request) {
        identityIamClient.createRole(request);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping
    public ApiResponse<List<RoleSummaryResponse>> listRoles() {
        List<RoleSummaryResponse> roles = identityIamClient.listRoles().getResult()
                .stream()
                .map(this::toRoleSummary)
                .toList();
        return ApiResponse.<List<RoleSummaryResponse>>builder().result(roles).build();
    }

    @GetMapping("/by-id/{roleId}")
    public ApiResponse<RoleSummaryResponse> getRoleById(@PathVariable String roleId) {
        return ApiResponse.<RoleSummaryResponse>builder()
                .result(toRoleSummary(identityIamClient.getRoleById(roleId).getResult()))
                .build();
    }

    @GetMapping("/by-name/{roleName}")
    public ApiResponse<RoleSummaryResponse> getRoleByName(@PathVariable String roleName) {
        return ApiResponse.<RoleSummaryResponse>builder()
                .result(toRoleSummary(identityIamClient.getRoleByName(roleName).getResult()))
                .build();
    }

    @PostMapping("/{roleName}/composites")
    public ApiResponse<Void> addCompositeRoles(@PathVariable String roleName, @RequestBody List<RoleRefRequest> roles) {
        identityIamClient.addCompositeRoles(roleName, roles);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/{roleName}/composites")
    public ApiResponse<List<RoleSummaryResponse>> getCompositeRoles(@PathVariable String roleName) {
        List<RoleSummaryResponse> roles = identityIamClient.getCompositeRoles(roleName).getResult()
                .stream()
                .map(this::toRoleSummary)
                .toList();
        return ApiResponse.<List<RoleSummaryResponse>>builder().result(roles).build();
    }

    @DeleteMapping("/{roleName}/composites")
    public ApiResponse<Void> removeCompositeRoles(@PathVariable String roleName, @RequestBody List<RoleRefRequest> roles) {
        identityIamClient.removeCompositeRoles(roleName, roles);
        return ApiResponse.<Void>builder().build();
    }

    @DeleteMapping("/{roleName}")
    public ApiResponse<Void> deleteRole(@PathVariable String roleName) {
        identityIamClient.deleteRole(roleName);
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

