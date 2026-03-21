package com.nexatech.user.service;

import java.util.List;

import com.nexatech.user.dto.request.RoleRequest;
import com.nexatech.user.dto.response.RoleResponse;

public interface RoleService {
    RoleResponse create(RoleRequest request);

    List<RoleResponse> getAll();

    RoleResponse getById(String id);

    RoleResponse update(String id, RoleRequest request);

    void delete(String id);
}
