package com.nexatech.user.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nexatech.user.dto.request.PermissionRequest;
import com.nexatech.user.dto.response.PermissionResponse;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;
import com.nexatech.user.mapper.PermissionMapper;
import com.nexatech.user.model.Permission;
import com.nexatech.user.repository.PermissionRepository;
import com.nexatech.user.service.PermissionService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PermissionServiceImpl implements PermissionService {
    PermissionRepository permissionRepository;
    PermissionMapper permissionMapper;

    @Override
    public PermissionResponse create(PermissionRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        if (permissionRepository.existsByName(request.getName())) {
            throw new AppException(ErrorCode.PERMISSION_NAME_EXISTS);
        }
        Permission entity = permissionMapper.toEntity(request);
        return permissionMapper.toResponse(permissionRepository.save(entity));
    }

    @Override
    public List<PermissionResponse> getAll() {
        return permissionRepository.findAllByActiveTrue()
                .stream()
                .map(permissionMapper::toResponse)
                .toList();
    }

    @Override
    public PermissionResponse getById(String id) {
        Permission permission = permissionRepository.findByIdAndActiveTrue(parseUuid(id))
                .orElseThrow(() -> new AppException(ErrorCode.PERMISSION_NOT_FOUND));
        return permissionMapper.toResponse(permission);
    }

    @Override
    public PermissionResponse update(String id, PermissionRequest request) {
        Permission permission = permissionRepository.findByIdAndActiveTrue(parseUuid(id))
                .orElseThrow(() -> new AppException(ErrorCode.PERMISSION_NOT_FOUND));

        if (request != null && request.getName() != null && !request.getName().isBlank()) {
            if (!request.getName().equalsIgnoreCase(permission.getName()) && permissionRepository.existsByName(request.getName())) {
                throw new AppException(ErrorCode.PERMISSION_NAME_EXISTS);
            }
        }

        if (request != null) {
            permissionMapper.update(permission, request);
        }
        return permissionMapper.toResponse(permissionRepository.save(permission));
    }

    @Override
    public void delete(String id) {
        Permission permission = permissionRepository.findByIdAndActiveTrue(parseUuid(id))
                .orElseThrow(() -> new AppException(ErrorCode.PERMISSION_NOT_FOUND));
        permission.setActive(false);
        permissionRepository.save(permission);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INVALID_UUID);
        }
    }
}
