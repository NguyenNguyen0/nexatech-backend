package com.nexatech.order.controller;

import com.nexatech.order.dto.response.ApiResponse;
import com.nexatech.order.dto.response.PageResponse;
import com.nexatech.order.enums.OrderStatus;
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

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> getAllOrders(Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(orderService.getAllOrders(pageable)));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<OrderResponse> updateStatus(
            @PathVariable UUID id,
            @RequestParam OrderStatus status) {
        return ApiResponse.ok(orderService.updateOrderStatus(id, status));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancelOrder(
            @RequestHeader("X-User-Id") UUID customerId,
            @PathVariable UUID id) {
        orderService.cancelOrder(customerId, id);
        return ApiResponse.ok(null);
    }
}
