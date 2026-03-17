package com.nexatech.user.controller;

import com.nexatech.user.dto.response.ApiResponse;
import com.nexatech.user.dto.AddressRequest;
import com.nexatech.user.dto.AddressResponse;
import com.nexatech.user.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public ApiResponse<List<AddressResponse>> getAddresses(@RequestHeader("X-User-Id") UUID userId) {
        return ApiResponse.ok(addressService.getAddresses(userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AddressResponse> createAddress(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AddressRequest request) {
        return ApiResponse.ok(addressService.createAddress(userId, request));
    }

    @PutMapping("/{addressId}")
    public ApiResponse<AddressResponse> updateAddress(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest request) {
        return ApiResponse.ok(addressService.updateAddress(userId, addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> deleteAddress(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID addressId) {
        addressService.deleteAddress(userId, addressId);
        return ApiResponse.ok(null);
    }
}
