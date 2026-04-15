package com.nexatech.user.service;

import com.nexatech.user.dto.request.AdminUserCreateRequest;
import com.nexatech.user.dto.response.UserResponse;

public interface AdminUserService {

    UserResponse createUserWithKeycloakProvision(AdminUserCreateRequest request);
}
