package com.nexatech.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.order.dto.CheckoutRequest;
import com.nexatech.order.dto.CheckoutResponse;
import com.nexatech.order.dto.response.ApiResponse;
import com.nexatech.order.service.CheckoutService;
import com.nexatech.order.service.IdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private static final String IDEMPOTENCY_OPERATION = "CHECKOUT";

    private final CheckoutService checkoutService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CheckoutResponse> checkout(
            @RequestHeader(value = "X-User-Id", required = false) UUID customerId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return handleIdempotentCheckout(customerId, idempotencyKey, request);
        }

        return ApiResponse.ok(checkoutService.checkout(customerId, request));
    }

    private ApiResponse<CheckoutResponse> handleIdempotentCheckout(
            UUID customerId,
            String idempotencyKey,
            CheckoutRequest request) {
        return idempotencyService.checkAndGetCached(idempotencyKey, IDEMPOTENCY_OPERATION)
                .map(payload -> ApiResponse.ok(readCachedCheckoutResponse(payload)))
                .orElseGet(() -> {
                    idempotencyService.markProcessing(idempotencyKey, IDEMPOTENCY_OPERATION);
                    try {
                        CheckoutResponse response = checkoutService.checkout(customerId, request);
                        idempotencyService.markCompleted(idempotencyKey, response.getOrderId(), response);
                        return ApiResponse.ok(response);
                    } catch (RuntimeException ex) {
                        idempotencyService.markFailed(idempotencyKey);
                        throw ex;
                    }
                });
    }

    private CheckoutResponse readCachedCheckoutResponse(String payload) {
        try {
            return objectMapper.readValue(payload, CheckoutResponse.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read cached checkout response");
        }
    }
}
