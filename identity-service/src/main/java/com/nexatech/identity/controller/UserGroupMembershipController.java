package com.nexatech.identity.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.identity.dto.base.ApiResponse;
import com.nexatech.identity.service.UserGroupMembershipService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/users/{userId}/groups")
public class UserGroupMembershipController {

    UserGroupMembershipService userGroupMembershipService;

    @PutMapping("/{groupId}")
    public ApiResponse<Void> addUserToGroup(@PathVariable String userId, @PathVariable String groupId) {
        userGroupMembershipService.addUserToGroup(userId, groupId);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getGroupsForUser(@PathVariable String userId) {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .result(userGroupMembershipService.getGroupsForUser(userId))
                .build();
    }

    @DeleteMapping("/{groupId}")
    public ApiResponse<Void> removeUserFromGroup(@PathVariable String userId, @PathVariable String groupId) {
        userGroupMembershipService.removeUserFromGroup(userId, groupId);
        return ApiResponse.<Void>builder().build();
    }
}
