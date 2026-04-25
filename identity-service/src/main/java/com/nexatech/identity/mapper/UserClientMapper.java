package com.nexatech.identity.mapper;

import org.springframework.stereotype.Component;

import com.nexatech.identity.dto.request.UserCreationParam;
import com.nexatech.identity.dto.request.UserClientRequest;

@Component
public class UserClientMapper {

    public UserClientRequest toProvisionRequest(String userId, UserCreationParam param) {
        return UserClientRequest.builder()
                .id(userId)
                .email(param.getEmail())
                .username(param.getUsername())
                .firstName(param.getFirstName())
                .lastName(param.getLastName())
                .avatarUrl(param.getAvatarUrl())
                .phone(param.getPhone())
                .dob(param.getDob())
                .gender(param.getGender())
                .onBoarded(param.getOnBoarded())
                .build();
    }
}
