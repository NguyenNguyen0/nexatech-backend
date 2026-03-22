package com.nexatech.user.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.request.AddressRequest;
import com.nexatech.user.dto.response.AddressResponse;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;
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
                        @AuthenticationPrincipal Jwt jwt,
                        @Valid @RequestBody AddressRequest request) {
                return ApiResponse.<AddressResponse>builder()
                                .result(addressService.createAddress(currentUserId(jwt), request))
                                .build();
        }

        @GetMapping
        public ApiResponse<List<AddressResponse>> findMyAddresses(@AuthenticationPrincipal Jwt jwt) {
                return ApiResponse.<List<AddressResponse>>builder()
                                .result(addressService.findMyAddresses(currentUserId(jwt)))
                                .build();
        }

        @GetMapping("/{id}")
        public ApiResponse<AddressResponse> findMyAddressById(@AuthenticationPrincipal Jwt jwt,
                        @PathVariable String id) {
                return ApiResponse.<AddressResponse>builder()
                                .result(addressService.findMyAddressById(currentUserId(jwt), id))
                                .build();
        }

        @PutMapping("/{id}")
        public ApiResponse<AddressResponse> updateMyAddress(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable String id,
                        @Valid @RequestBody AddressRequest request) {
                return ApiResponse.<AddressResponse>builder()
                                .result(addressService.updateMyAddress(currentUserId(jwt), id, request))
                                .build();
        }

        @PutMapping("/{id}/default")
        public ApiResponse<AddressResponse> setDefaultMyAddress(@AuthenticationPrincipal Jwt jwt,
                        @PathVariable String id) {
                return ApiResponse.<AddressResponse>builder()
                                .result(addressService.setDefaultMyAddress(currentUserId(jwt), id))
                                .build();
        }

        @DeleteMapping("/{id}")
        public ApiResponse<Void> deleteMyAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
                addressService.deleteMyAddress(currentUserId(jwt), id);
                return ApiResponse.<Void>builder().build();
        }

        private String currentUserId(Jwt jwt) {
                if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
                        throw new AppException(ErrorCode.UNAUTHENTICATED);
                }
                return jwt.getSubject();
        }
}
