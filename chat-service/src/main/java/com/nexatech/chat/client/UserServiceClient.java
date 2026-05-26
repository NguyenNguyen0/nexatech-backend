package com.nexatech.chat.client;

import com.nexatech.chat.client.dto.UserProfileResponse;
import com.nexatech.chat.client.dto.UserServiceApiResponse;
import com.nexatech.chat.config.UserServiceClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "user-service",
        url = "${openfeign.user-service.url:}",
        path = "/internal/users",
        configuration = UserServiceClientConfig.class)
public interface UserServiceClient {

    @GetMapping("/{userId}")
    UserServiceApiResponse<UserProfileResponse> getUserById(@PathVariable("userId") String userId);
}
