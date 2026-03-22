package com.nexatech.user.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.user.client.IdentityIamClient;
import com.nexatech.user.constant.Permissions;
import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.request.GroupCreateRequest;
import com.nexatech.user.dto.request.RoleRefRequest;
import com.nexatech.user.dto.response.GroupMemberResponse;
import com.nexatech.user.dto.response.GroupSummaryResponse;
import com.nexatech.user.security.RequiresPermission;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GroupController {

    IdentityIamClient identityIamClient;

    @PostMapping
    @RequiresPermission(Permissions.Group.WRITE)
    public ApiResponse<String> createGroup(@Valid @RequestBody GroupCreateRequest request) {
        return ApiResponse.<String>builder().result(identityIamClient.createGroup(request).getResult()).build();
    }

    @GetMapping
    @RequiresPermission(Permissions.Group.READ)
    public ApiResponse<List<GroupSummaryResponse>> listGroups(
            @RequestParam(value = "search", required = false) String search) {
        List<GroupSummaryResponse> groups = identityIamClient.listGroups(search).getResult()
                .stream()
                .map(this::toGroupSummary)
                .toList();
        return ApiResponse.<List<GroupSummaryResponse>>builder().result(groups).build();
    }

    @GetMapping("/{groupId}/role-mappings")
    @RequiresPermission(Permissions.Group.READ)
    public ApiResponse<Map<String, Object>> getGroupRoleMappings(@PathVariable String groupId) {
        return ApiResponse.<Map<String, Object>>builder()
                .result(identityIamClient.getGroupRoleMappings(groupId).getResult())
                .build();
    }

    @PostMapping("/{groupId}/realm-role-mappings")
    @RequiresPermission(Permissions.Group.WRITE)
    public ApiResponse<Void> assignRealmRoles(@PathVariable String groupId,
            @Valid @RequestBody List<RoleRefRequest> roles) {
        identityIamClient.assignRealmRoles(groupId, roles);
        return ApiResponse.<Void>builder().build();
    }

    @DeleteMapping("/{groupId}/realm-role-mappings")
    @RequiresPermission(Permissions.Group.WRITE)
    public ApiResponse<Void> removeRealmRoles(@PathVariable String groupId,
            @Valid @RequestBody List<RoleRefRequest> roles) {
        identityIamClient.removeRealmRoles(groupId, roles);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/{groupId}/members")
    @RequiresPermission(Permissions.Group.READ)
    public ApiResponse<List<GroupMemberResponse>> getGroupMembers(@PathVariable String groupId) {
        List<GroupMemberResponse> members = identityIamClient.getGroupMembers(groupId).getResult()
                .stream()
                .map(this::toMemberSummary)
                .toList();
        return ApiResponse.<List<GroupMemberResponse>>builder().result(members).build();
    }

    @PutMapping("/{groupId}/users/{userId}")
    @RequiresPermission(Permissions.Group.WRITE)
    public ApiResponse<Void> addUserToGroup(@PathVariable String groupId, @PathVariable String userId) {
        identityIamClient.addUserToGroup(userId, groupId);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/users/{userId}")
    @RequiresPermission(Permissions.Group.READ)
    public ApiResponse<List<GroupSummaryResponse>> getGroupsForUser(@PathVariable String userId) {
        List<GroupSummaryResponse> groups = identityIamClient.getGroupsForUser(userId).getResult()
                .stream()
                .map(this::toGroupSummary)
                .toList();
        return ApiResponse.<List<GroupSummaryResponse>>builder().result(groups).build();
    }

    @DeleteMapping("/{groupId}/users/{userId}")
    @RequiresPermission(Permissions.Group.WRITE)
    public ApiResponse<Void> removeUserFromGroup(@PathVariable String groupId, @PathVariable String userId) {
        identityIamClient.removeUserFromGroup(userId, groupId);
        return ApiResponse.<Void>builder().build();
    }

    @DeleteMapping("/{groupId}")
    @RequiresPermission(Permissions.Group.WRITE)
    public ApiResponse<Void> deleteGroup(@PathVariable String groupId) {
        identityIamClient.deleteGroup(groupId);
        return ApiResponse.<Void>builder().build();
    }

    private GroupSummaryResponse toGroupSummary(Map<String, Object> source) {
        if (source == null) {
            return new GroupSummaryResponse();
        }
        return GroupSummaryResponse.builder()
                .id(getString(source.get("id")))
                .name(getString(source.get("name")))
                .path(getString(source.get("path")))
                .build();
    }

    private GroupMemberResponse toMemberSummary(Map<String, Object> source) {
        if (source == null) {
            return new GroupMemberResponse();
        }
        return GroupMemberResponse.builder()
                .id(getString(source.get("id")))
                .username(getString(source.get("username")))
                .email(getString(source.get("email")))
                .firstName(getString(source.get("firstName")))
                .lastName(getString(source.get("lastName")))
                .build();
    }

    private String getString(Object value) {
        return value == null ? null : value.toString();
    }
}
