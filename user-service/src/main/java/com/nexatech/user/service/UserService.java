package com.nexatech.user.service;

import com.nexatech.user.dto.base.PageResponse;
import com.nexatech.user.dto.request.UserCreationRequest;
import com.nexatech.user.dto.request.UserUpdatenRequest;
import com.nexatech.user.dto.response.UserResponse;

public interface UserService {
    
    UserResponse createUser(UserCreationRequest request);

    PageResponse<UserResponse> getAllUsers(int page, int size);

    UserResponse getUserById(String id);

    UserResponse updateUser(String id, UserUpdatenRequest dto);

    void deleteUser(String id);

    void enableUser(String id);
}
