package com.nexatech.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.request.AddressRequest;
import com.nexatech.user.dto.response.AddressResponse;
import com.nexatech.user.service.AddressService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AddressController {

    AddressService addressService;

    @PostMapping
    public ApiResponse<AddressResponse> create(@RequestBody AddressRequest request) {
        return ApiResponse.<AddressResponse>builder()
                .result(addressService.create(request))
                .build();
    }

    @GetMapping
    public ApiResponse<List<AddressResponse>> getAll(@RequestParam(value = "userId", required = false) String userId) {
        List<AddressResponse> result = (userId == null || userId.isBlank())
                ? addressService.getAll()
                : addressService.getByUserId(userId);

        return ApiResponse.<List<AddressResponse>>builder()
                .result(result)
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<AddressResponse> getById(@PathVariable String id) {
        return ApiResponse.<AddressResponse>builder()
                .result(addressService.getById(id))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<AddressResponse> update(@PathVariable String id, @RequestBody AddressRequest request) {
        return ApiResponse.<AddressResponse>builder()
                .result(addressService.update(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        addressService.delete(id);
        return ApiResponse.<Void>builder().build();
    }
}

