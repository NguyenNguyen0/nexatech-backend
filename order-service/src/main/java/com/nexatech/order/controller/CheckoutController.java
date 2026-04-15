package com.nexatech.order.controller;

import com.nexatech.order.dto.CheckoutRequest;
import com.nexatech.order.dto.CheckoutResponse;
import com.nexatech.order.dto.response.ApiResponse;
import com.nexatech.order.service.CheckoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CheckoutResponse> checkout(
            @RequestHeader("X-User-Id") UUID customerId,
            @Valid @RequestBody CheckoutRequest request) {
        return ApiResponse.ok(checkoutService.checkout(customerId, request));
    }
}
