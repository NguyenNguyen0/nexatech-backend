package com.nexatech.product.client;

import com.nexatech.product.client.dto.UserProfileResponse;
import com.nexatech.product.client.dto.UserServiceApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", path = "/internal/users")
public interface UserClient {

    @GetMapping("/{userId}")
    UserServiceApiResponse<UserProfileResponse> getUserById(@PathVariable("userId") String userId);
}
