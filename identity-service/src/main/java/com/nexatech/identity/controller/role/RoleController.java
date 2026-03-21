package com.nexatech.identity.controller.role;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.identity.dto.base.ApiResponse;
import com.nexatech.identity.dto.request.KeycloakRoleRef;
import com.nexatech.identity.dto.request.RoleCreateParam;
import com.nexatech.identity.service.role.RoleService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/roles")
public class RoleController {

    RoleService roleService;

    @PostMapping
    public ApiResponse<Void> createRole(@RequestBody RoleCreateParam param) {
        roleService.createRole(param);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listRoles() {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .result(roleService.listRoles())
                .build();
    }

    @GetMapping("/by-id/{roleId}")
    public ApiResponse<Map<String, Object>> getRoleById(@PathVariable String roleId) {
        return ApiResponse.<Map<String, Object>>builder()
                .result(roleService.getRoleById(roleId))
                .build();
    }

    @GetMapping("/by-name/{roleName}")
    public ApiResponse<Map<String, Object>> getRoleByName(@PathVariable String roleName) {
        return ApiResponse.<Map<String, Object>>builder()
                .result(roleService.getRoleByName(roleName))
                .build();
    }

    @PostMapping("/{roleName}/composites")
    public ApiResponse<Void> addCompositeRoles(@PathVariable String roleName, @RequestBody List<KeycloakRoleRef> roles) {
        roleService.addCompositeRoles(roleName, roles);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/{roleName}/composites")
    public ApiResponse<List<Map<String, Object>>> getCompositeRoles(@PathVariable String roleName) {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .result(roleService.getCompositeRoles(roleName))
                .build();
    }

    @DeleteMapping("/{roleName}/composites")
    public ApiResponse<Void> removeCompositeRoles(@PathVariable String roleName, @RequestBody List<KeycloakRoleRef> roles) {
        roleService.removeCompositeRoles(roleName, roles);
        return ApiResponse.<Void>builder().build();
    }

    @DeleteMapping("/{roleName}")
    public ApiResponse<Void> deleteRole(@PathVariable String roleName) {
        roleService.deleteRole(roleName);
        return ApiResponse.<Void>builder().build();
    }
}
