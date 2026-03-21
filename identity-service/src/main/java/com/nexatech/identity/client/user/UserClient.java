package com.nexatech.identity.client.user;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.nexatech.identity.dto.base.ApiResponse;
import com.nexatech.identity.dto.request.UserClientRequest;

@FeignClient(name = "user-service", url = "${openfeign.user-service.url}")
public interface UserClient {

    @PostMapping("/users")
    ApiResponse<?> createUser(@RequestBody UserClientRequest request);
}
