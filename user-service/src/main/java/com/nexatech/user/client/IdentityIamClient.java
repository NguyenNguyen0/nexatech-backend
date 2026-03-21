package com.nexatech.user.client;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.request.GroupCreateRequest;
import com.nexatech.user.dto.request.RoleCreateRequest;
import com.nexatech.user.dto.request.RoleRefRequest;

@FeignClient(name = "identity-iam", url = "${openfeign.identity-service.url}")
public interface IdentityIamClient {

    @PostMapping("/internal/roles")
    ApiResponse<Void> createRole(@RequestBody RoleCreateRequest request);

    @GetMapping("/internal/roles")
    ApiResponse<List<Map<String, Object>>> listRoles();

    @GetMapping("/internal/roles/by-id/{roleId}")
    ApiResponse<Map<String, Object>> getRoleById(@PathVariable("roleId") String roleId);

    @GetMapping("/internal/roles/by-name/{roleName}")
    ApiResponse<Map<String, Object>> getRoleByName(@PathVariable("roleName") String roleName);

    @PostMapping("/internal/roles/{roleName}/composites")
    ApiResponse<Void> addCompositeRoles(@PathVariable("roleName") String roleName, @RequestBody List<RoleRefRequest> roles);

    @GetMapping("/internal/roles/{roleName}/composites")
    ApiResponse<List<Map<String, Object>>> getCompositeRoles(@PathVariable("roleName") String roleName);

    @DeleteMapping("/internal/roles/{roleName}/composites")
    ApiResponse<Void> removeCompositeRoles(@PathVariable("roleName") String roleName,
            @RequestBody List<RoleRefRequest> roles);

    @DeleteMapping("/internal/roles/{roleName}")
    ApiResponse<Void> deleteRole(@PathVariable("roleName") String roleName);

    @PostMapping("/internal/groups")
    ApiResponse<String> createGroup(@RequestBody GroupCreateRequest request);

    @GetMapping("/internal/groups")
    ApiResponse<List<Map<String, Object>>> listGroups(@RequestParam(value = "search", required = false) String search);

    @GetMapping("/internal/groups/{groupId}/role-mappings")
    ApiResponse<Map<String, Object>> getGroupRoleMappings(@PathVariable("groupId") String groupId);

    @PostMapping("/internal/groups/{groupId}/realm-role-mappings")
    ApiResponse<Void> assignRealmRoles(@PathVariable("groupId") String groupId, @RequestBody List<RoleRefRequest> roles);

    @DeleteMapping("/internal/groups/{groupId}/realm-role-mappings")
    ApiResponse<Void> removeRealmRoles(@PathVariable("groupId") String groupId, @RequestBody List<RoleRefRequest> roles);

    @GetMapping("/internal/groups/{groupId}/members")
    ApiResponse<List<Map<String, Object>>> getGroupMembers(@PathVariable("groupId") String groupId);

    @DeleteMapping("/internal/groups/{groupId}")
    ApiResponse<Void> deleteGroup(@PathVariable("groupId") String groupId);

    @PutMapping("/internal/users/{userId}/groups/{groupId}")
    ApiResponse<Void> addUserToGroup(@PathVariable("userId") String userId, @PathVariable("groupId") String groupId);

    @GetMapping("/internal/users/{userId}/groups")
    ApiResponse<List<Map<String, Object>>> getGroupsForUser(@PathVariable("userId") String userId);

    @DeleteMapping("/internal/users/{userId}/groups/{groupId}")
    ApiResponse<Void> removeUserFromGroup(@PathVariable("userId") String userId, @PathVariable("groupId") String groupId);
}
