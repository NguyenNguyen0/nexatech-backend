package com.nexatech.user.service;

import java.util.List;

import com.nexatech.user.dto.request.PermissionRequest;
import com.nexatech.user.dto.response.PermissionResponse;

public interface PermissionService {
    PermissionResponse create(PermissionRequest request);

    List<PermissionResponse> getAll();

    PermissionResponse getById(String id);

    PermissionResponse update(String id, PermissionRequest request);

    void delete(String id);
}
