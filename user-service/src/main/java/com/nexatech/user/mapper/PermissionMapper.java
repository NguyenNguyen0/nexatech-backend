package com.nexatech.user.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.nexatech.user.dto.request.PermissionRequest;
import com.nexatech.user.dto.response.PermissionResponse;
import com.nexatech.user.model.Permission;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionMapper {
    Permission toEntity(PermissionRequest request);

    PermissionResponse toResponse(Permission permission);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void update(@MappingTarget Permission permission, PermissionRequest request);
}

