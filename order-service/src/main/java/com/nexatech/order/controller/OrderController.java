package com.nexatech.order.controller;

import com.nexatech.order.dto.response.ApiResponse;
import com.nexatech.order.dto.response.PageResponse;
import com.nexatech.order.dto.CreateOrderRequest;
import com.nexatech.order.dto.OrderResponse;
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

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> createOrder(
            @RequestHeader("X-User-Id") UUID customerId,
            @Valid @RequestBody CreateOrderRequest request) {
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
}
