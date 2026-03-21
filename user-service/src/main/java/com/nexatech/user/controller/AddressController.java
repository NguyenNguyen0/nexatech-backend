package com.nexatech.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.request.AddressRequest;
import com.nexatech.user.dto.response.AddressResponse;
import com.nexatech.user.service.AddressService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/users/me/addresses")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AddressController {

    AddressService addressService;

    @PostMapping
    public ApiResponse<AddressResponse> createAddress(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody AddressRequest request) {
        return ApiResponse.<AddressResponse>builder()
                .result(addressService.createAddress(userId, request))
                .build();
    }

    @GetMapping
    public ApiResponse<List<AddressResponse>> findMyAddresses(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ApiResponse.<List<AddressResponse>>builder()
                .result(addressService.findMyAddresses(userId))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<AddressResponse> findMyAddressById(
            @RequestHeader(value = "X-User-Id", required = false) String userId, @PathVariable String id) {
        return ApiResponse.<AddressResponse>builder()
                .result(addressService.findMyAddressById(userId, id))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<AddressResponse> updateMyAddress(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String id,
            @RequestBody AddressRequest request) {
        return ApiResponse.<AddressResponse>builder()
                .result(addressService.updateMyAddress(userId, id, request))
                .build();
    }

    @PutMapping("/{id}/default")
    public ApiResponse<AddressResponse> setDefaultMyAddress(
            @RequestHeader(value = "X-User-Id", required = false) String userId, @PathVariable String id) {
        return ApiResponse.<AddressResponse>builder()
                .result(addressService.setDefaultMyAddress(userId, id))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteMyAddress(@RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String id) {
        addressService.deleteMyAddress(userId, id);
        return ApiResponse.<Void>builder().build();
    }
}
