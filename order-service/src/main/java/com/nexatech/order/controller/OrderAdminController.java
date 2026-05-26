package com.nexatech.order.controller;

import com.nexatech.order.dto.OrderResponse;
import com.nexatech.order.dto.OrderStatsResponse;
import com.nexatech.order.dto.ReturnResponse;
import com.nexatech.order.dto.UpdateShippingRequest;
import com.nexatech.order.dto.response.ApiResponse;
import com.nexatech.order.dto.response.PageResponse;
import com.nexatech.order.enums.OrderStatus;
import com.nexatech.order.enums.ReturnStatus;
import com.nexatech.order.model.AuditLog;
import com.nexatech.order.service.OrderReturnService;
import com.nexatech.order.service.OrderService;
import com.nexatech.order.service.OrderStatsService;
import com.nexatech.order.service.PointInTimeQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderStatsService orderStatsService;
    private final OrderService orderService;
    private final OrderReturnService returnService;
    private final PointInTimeQueryService pointInTimeQueryService;

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(orderService.getAllOrders(status, pageable)));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<OrderResponse> updateStatus(
            @PathVariable UUID id,
            @RequestParam OrderStatus status) {
        return ApiResponse.ok(orderService.updateOrderStatus(id, status));
    }

    @PatchMapping("/{id}/shipping")
    public ApiResponse<OrderResponse> updateShipping(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShippingRequest request) {
        return ApiResponse.ok(orderService.updateShipping(id, request));
    }

    @GetMapping("/{id}/timeline")
    public ApiResponse<List<AuditLog>> getTimeline(@PathVariable UUID id) {
        return ApiResponse.ok(pointInTimeQueryService.getTimeline(id));
    }

    @GetMapping("/{id}/state-at")
    public ApiResponse<Map<String, Object>> getStateAt(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant t) {
        return ApiResponse.ok(pointInTimeQueryService.getStateAt("Order", id, t));
    }

    @GetMapping("/trace/{correlationId}")
    public ApiResponse<List<AuditLog>> traceByCorrelation(@PathVariable String correlationId) {
        return ApiResponse.ok(pointInTimeQueryService.getTimelineByCorrelation(correlationId));
    }

    @GetMapping("/stats")
    public ApiResponse<OrderStatsResponse> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(orderStatsService.getStats(from, to));
    }

    @GetMapping("/returns")
    public ApiResponse<PageResponse<ReturnResponse>> getAllReturns(
            @RequestParam(required = false) ReturnStatus status,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(returnService.getAllReturns(status, pageable)));
    }
}
