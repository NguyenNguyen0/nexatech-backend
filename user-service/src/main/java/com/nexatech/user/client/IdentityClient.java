package com.nexatech.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.request.IdentityUserUpdateRequest;

@FeignClient(name = "identity-service", url = "${openfeign.identity-service.url}")
public interface IdentityClient {

    @PutMapping("/users/{userId}")
    ApiResponse<Void> updateUser(@PathVariable("userId") String userId,
            @RequestBody IdentityUserUpdateRequest request);
}
