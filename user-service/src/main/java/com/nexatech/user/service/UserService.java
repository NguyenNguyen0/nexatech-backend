package com.nexatech.user.service;

import java.util.List;
import com.nexatech.user.dto.request.UserCreationRequest;
import com.nexatech.user.dto.request.UserUpdatenRequest;
import com.nexatech.user.dto.response.UserResponse;

public interface UserService {
    
    UserResponse createUser(UserCreationRequest request);

    List<UserResponse> getAllUsers();

    UserResponse getUserById(String id);

    UserResponse updateUser(String id, UserUpdatenRequest dto);

    void deleteUser(String id);
}
