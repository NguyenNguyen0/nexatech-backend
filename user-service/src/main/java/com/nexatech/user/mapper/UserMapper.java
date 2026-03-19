package com.nexatech.user.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.nexatech.user.dto.request.UserCreationRequest;
import com.nexatech.user.dto.request.UserUpdatenRequest;
import com.nexatech.user.dto.response.UserResponse;
import com.nexatech.user.model.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toUser(UserCreationRequest request);

    UserResponse toResponse(User user);

    void updateUser(UserUpdatenRequest request, @MappingTarget User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUser(@MappingTarget User user, UserUpdatenRequest request);
}
