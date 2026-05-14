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
import com.nexatech.user.dto.event.KeycloakUserProvisionPayload;
import com.nexatech.user.dto.request.IdentityUserUpdateRequest;
import com.nexatech.user.dto.request.RoleRefRequest;

@FeignClient(name = "identity-service", url = "${openfeign.identity-service.url}")
public interface IdentityClient {

        @PostMapping("/internal/users")
        ApiResponse<String> createKeycloakUser(@RequestBody KeycloakUserProvisionPayload param);

        @GetMapping("/internal/users/availability/email")
        ApiResponse<Boolean> isKeycloakEmailAvailable(
                        @RequestParam("email") String email, @RequestParam("excludeUserId") String excludeUserId);

        @PutMapping("/internal/users/{userId}")
        ApiResponse<Void> updateUser(@PathVariable("userId") String userId,
                        @RequestBody IdentityUserUpdateRequest request);

        @GetMapping("/internal/users/{userId}/role-mappings")
        ApiResponse<Map<String, Object>> getUserRoleMappings(@PathVariable("userId") String userId);

        @PostMapping("/internal/users/{userId}/realm-role-mappings")
        ApiResponse<Void> assignRealmRolesToUser(
                        @PathVariable("userId") String userId,
                        @RequestBody List<RoleRefRequest> roles);

        @DeleteMapping("/internal/users/{userId}/realm-role-mappings")
        ApiResponse<Void> removeRealmRolesFromUser(
                        @PathVariable("userId") String userId,
                        @RequestBody List<RoleRefRequest> roles);
}
