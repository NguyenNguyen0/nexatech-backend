package com.nexatech.user.service;

import com.nexatech.user.dto.request.AdminUserCreateRequest;
import com.nexatech.user.dto.response.UserResponse;

public interface AdminUserService {

    UserResponse createUserWithKeycloakProvision(AdminUserCreateRequest request);

    void updateUserRole(String userId, String roleName);

    void removeUserRole(String userId, String roleName);
}
