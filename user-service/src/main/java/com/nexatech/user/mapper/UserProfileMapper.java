package com.nexatech.user.mapper;

import com.nexatech.user.dto.UserProfileResponse;
import com.nexatech.user.model.UserProfile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {

    UserProfileResponse toResponse(UserProfile entity);
}
