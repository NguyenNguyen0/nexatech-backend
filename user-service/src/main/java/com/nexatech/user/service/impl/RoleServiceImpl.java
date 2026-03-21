package com.nexatech.user.service.impl;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.nexatech.user.dto.request.RoleRequest;
import com.nexatech.user.dto.response.PermissionResponse;
import com.nexatech.user.dto.response.RoleResponse;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;
import com.nexatech.user.mapper.PermissionMapper;
import com.nexatech.user.model.Permission;
import com.nexatech.user.model.Role;
import com.nexatech.user.repository.PermissionRepository;
import com.nexatech.user.repository.RoleRepository;
import com.nexatech.user.service.RoleService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoleServiceImpl implements RoleService {
    RoleRepository roleRepository;
    PermissionRepository permissionRepository;
    PermissionMapper permissionMapper;

    @Override
    public RoleResponse create(RoleRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        if (roleRepository.existsByName(request.getName())) {
            throw new AppException(ErrorCode.ROLE_NAME_EXISTS);
        }

        Role role = Role.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        role.setPermissions(resolvePermissions(request.getPermissionIds()));
        return toResponse(roleRepository.save(role));
    }

    @Override
    public List<RoleResponse> getAll() {
        return roleRepository.findAllByActiveTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public RoleResponse getById(String id) {
        Role role = roleRepository.findByIdAndActiveTrue(parseUuid(id))
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        return toResponse(role);
    }

    @Override
    public RoleResponse update(String id, RoleRequest request) {
        Role role = roleRepository.findByIdAndActiveTrue(parseUuid(id))
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        if (request != null && request.getName() != null && !request.getName().isBlank()) {
            if (!request.getName().equalsIgnoreCase(role.getName()) && roleRepository.existsByName(request.getName())) {
                throw new AppException(ErrorCode.ROLE_NAME_EXISTS);
            }
            role.setName(request.getName());
        }

        if (request != null && request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        if (request != null && request.getPermissionIds() != null) {
            role.setPermissions(resolvePermissions(request.getPermissionIds()));
        }

        return toResponse(roleRepository.save(role));
    }

    @Override
    public void delete(String id) {
        Role role = roleRepository.findByIdAndActiveTrue(parseUuid(id))
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        role.setActive(false);
        roleRepository.save(role);
    }

    private Set<Permission> resolvePermissions(Set<UUID> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) return Set.of();
        List<Permission> permissions = permissionRepository.findAllById(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            throw new AppException(ErrorCode.PERMISSION_NOT_FOUND);
        }
        return permissions.stream().collect(Collectors.toSet());
    }

    private RoleResponse toResponse(Role role) {
        Set<PermissionResponse> permissions = role.getPermissions() == null
                ? Set.of()
                : role.getPermissions().stream().map(permissionMapper::toResponse).collect(Collectors.toSet());

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(permissions)
                .active(role.getActive())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INVALID_UUID);
        }
    }
}
