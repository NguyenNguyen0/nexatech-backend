package com.nexatech.order.controller;

import com.nexatech.order.dto.OrderResponse;
import com.nexatech.order.dto.OrderStatsResponse;
import com.nexatech.order.dto.UpdateShippingRequest;
import com.nexatech.order.dto.response.ApiResponse;
import com.nexatech.order.service.OrderService;
import com.nexatech.order.service.OrderStatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * API admin: thống kê đơn hàng và cập nhật thông tin vận chuyển.
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderStatsService orderStatsService;
    private final OrderService orderService;

    /**
     * Thống kê tổng quan đơn hàng theo khoảng thời gian.
     * GET /api/v1/admin/orders/stats?from=2026-01-01&to=2026-03-31
     */
    @GetMapping("/stats")
    public ApiResponse<OrderStatsResponse> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(orderStatsService.getStats(from, to));
    }

    /**
     * Cập nhật thông tin vận chuyển cho đơn hàng (tracking number, đơn vị, ngày dự kiến).
     * PATCH /api/v1/admin/orders/{id}/shipping
     */
    @PatchMapping("/{id}/shipping")
    public ApiResponse<OrderResponse> updateShipping(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShippingRequest request) {
        return ApiResponse.ok(orderService.updateShipping(id, request));
    }
}
