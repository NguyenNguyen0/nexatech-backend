package com.nexatech.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.order.dto.response.ApiResponse;
import com.nexatech.order.dto.response.PageResponse;
import com.nexatech.order.dto.CreateOrderRequest;
import com.nexatech.order.dto.OrderResponse;
import com.nexatech.order.service.IdempotencyService;
import com.nexatech.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final String IDEMPOTENCY_OPERATION = "CREATE_ORDER";

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> createOrder(
            @RequestHeader("X-User-Id") UUID customerId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return handleIdempotentCreateOrder(customerId, idempotencyKey, request);
        }

        return ApiResponse.ok(orderService.createOrder(customerId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable UUID id) {
        return ApiResponse.ok(orderService.getOrder(id));
    }

    @GetMapping("/my")
    public ApiResponse<PageResponse<OrderResponse>> getMyOrders(
            @RequestHeader("X-User-Id") UUID customerId,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(orderService.getMyOrders(customerId, pageable)));
    }

    @GetMapping("/check-purchased")
    public ApiResponse<Boolean> checkPurchased(
            @RequestParam UUID userId,
            @RequestParam String productId) {
        return ApiResponse.ok(orderService.hasPurchasedProduct(userId, productId));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(
            @RequestHeader("X-User-Id") UUID customerId,
            @PathVariable UUID id) {
        return ApiResponse.ok(orderService.cancelOrder(customerId, id));
    }

    private ApiResponse<OrderResponse> handleIdempotentCreateOrder(
            UUID customerId,
            String idempotencyKey,
            CreateOrderRequest request) {
        return idempotencyService.checkAndGetCached(idempotencyKey, IDEMPOTENCY_OPERATION)
                .map(payload -> ApiResponse.ok(readCachedOrderResponse(payload)))
                .orElseGet(() -> {
                    idempotencyService.markProcessing(idempotencyKey, IDEMPOTENCY_OPERATION);
                    try {
                        OrderResponse response = orderService.createOrder(customerId, request);
                        idempotencyService.markCompleted(idempotencyKey, response.getId(), response);
                        return ApiResponse.ok(response);
                    } catch (RuntimeException ex) {
                        idempotencyService.markFailed(idempotencyKey);
                        throw ex;
                    }
                });
    }

    private OrderResponse readCachedOrderResponse(String payload) {
        try {
            return objectMapper.readValue(payload, OrderResponse.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read cached order response");
        }
    }
}
