package com.nexatech.identity.controller.internal;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.identity.dto.base.ApiResponse;
import com.nexatech.identity.dto.request.GroupCreateParam;
import com.nexatech.identity.dto.request.KeycloakRoleRef;
import com.nexatech.identity.service.GroupService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/internal/groups")
public class InternalGroupController {

    GroupService groupService;

    @PostMapping
    public ApiResponse<String> createGroup(@RequestBody GroupCreateParam param) {
        return ApiResponse.<String>builder().result(groupService.createGroup(param)).build();
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listGroups(
            @RequestParam(value = "search", required = false) String search) {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .result(groupService.listGroups(search))
                .build();
    }

    @GetMapping("/{groupId}/role-mappings")
    public ApiResponse<Map<String, Object>> getGroupRoleMappings(@PathVariable String groupId) {
        return ApiResponse.<Map<String, Object>>builder()
                .result(groupService.getGroupRoleMappings(groupId))
                .build();
    }

    @PostMapping("/{groupId}/realm-role-mappings")
    public ApiResponse<Void> assignRealmRoles(@PathVariable String groupId, @RequestBody List<KeycloakRoleRef> roles) {
        groupService.assignRealmRolesToGroup(groupId, roles);
        return ApiResponse.<Void>builder().build();
    }

    @DeleteMapping("/{groupId}/realm-role-mappings")
    public ApiResponse<Void> removeRealmRoles(@PathVariable String groupId, @RequestBody List<KeycloakRoleRef> roles) {
        groupService.removeRealmRolesFromGroup(groupId, roles);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/{groupId}/members")
    public ApiResponse<List<Map<String, Object>>> getGroupMembers(@PathVariable String groupId) {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .result(groupService.getGroupMembers(groupId))
                .build();
    }

    @DeleteMapping("/{groupId}")
    public ApiResponse<Void> deleteGroup(@PathVariable String groupId) {
        groupService.deleteGroup(groupId);
        return ApiResponse.<Void>builder().build();
    }
}
