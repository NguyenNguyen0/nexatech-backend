package com.nexatech.identity.controller.user;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.identity.dto.base.ApiResponse;
import com.nexatech.identity.dto.request.Credential;
import com.nexatech.identity.dto.request.KeycloakRoleRef;
import com.nexatech.identity.dto.request.UserCreationParam;
import com.nexatech.identity.dto.request.UserUpdateParam;
import com.nexatech.identity.service.user.UserService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/users")
public class UserController {

    UserService userService;

    @PostMapping
    public ApiResponse<String> createUser(@RequestBody UserCreationParam param) {
        return ApiResponse.<String>builder().result(userService.createUser(param)).build();
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listUsers(
            @RequestParam(value = "username", required = false) String username) {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .result(userService.listUsers(username))
                .build();
    }

    @GetMapping("/{userId}")
    public ApiResponse<Map<String, Object>> getUser(@PathVariable String userId) {
        return ApiResponse.<Map<String, Object>>builder()
                .result(userService.getUserById(userId))
                .build();
    }

    @PutMapping("/{userId}")
    public ApiResponse<Void> updateUser(@PathVariable String userId, @RequestBody UserUpdateParam param) {
        userService.updateUser(userId, param);
        return ApiResponse.<Void>builder().build();
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/{userId}/credentials")
    public ApiResponse<List<Map<String, Object>>> getCredentials(@PathVariable String userId) {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .result(userService.getCredentials(userId))
                .build();
    }

    @PutMapping("/{userId}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable String userId, @RequestBody Credential credential) {
        userService.resetPassword(userId, credential);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/{userId}/role-mappings")
    public ApiResponse<Map<String, Object>> getRoleMappings(@PathVariable String userId) {
        return ApiResponse.<Map<String, Object>>builder()
                .result(userService.getRoleMappings(userId))
                .build();
    }

    @GetMapping("/{userId}/sessions")
    public ApiResponse<List<Map<String, Object>>> getSessions(@PathVariable String userId) {
        return ApiResponse.<List<Map<String, Object>>>builder()
                .result(userService.getSessions(userId))
                .build();
    }

    @PostMapping("/{userId}/realm-role-mappings")
    public ApiResponse<Void> assignRealmRoles(@PathVariable String userId, @RequestBody List<KeycloakRoleRef> roles) {
        userService.assignRealmRolesToUser(userId, roles);
        return ApiResponse.<Void>builder().build();
    }

    @DeleteMapping("/{userId}/realm-role-mappings")
    public ApiResponse<Void> removeRealmRoles(@PathVariable String userId, @RequestBody List<KeycloakRoleRef> roles) {
        userService.removeRealmRolesFromUser(userId, roles);
        return ApiResponse.<Void>builder().build();
    }
}
