package com.nexatech.order.controller;

import com.nexatech.order.dto.response.ApiResponse;
import com.nexatech.order.model.AuditLog;
import com.nexatech.order.service.PointInTimeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoint debug/admin để trace audit log và point-in-time query cho Order.
 *
 * GET /api/v1/admin/audit/orders/{orderId}/timeline
 *      → Toàn bộ lịch sử sự kiện của 1 đơn hàng (theo thứ tự thời gian)
 *
 * GET /api/v1/admin/audit/orders/{orderId}/state-at?t=2025-05-26T10:00:00Z
 *      → State của đơn hàng tại thời điểm T (reconstruct từ snapshot + delta audit logs)
 *
 * GET /api/v1/admin/audit/trace/{correlationId}
 *      → Toàn bộ sự kiện trong 1 request xuyên service (theo OpenTelemetry traceId)
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
public class AuditTraceController {

    private final PointInTimeQueryService pointInTimeQueryService;

    /**
     * Lịch sử sự kiện đầy đủ của 1 đơn hàng.
     * Mỗi entry có: action, entityType, entityId, payload (JSONB), correlationId, createdAt
     *
     * Ví dụ:
     *   GET /api/v1/admin/audit/orders/3f2a1b.../timeline
     */
    @GetMapping("/orders/{orderId}/timeline")
    public ApiResponse<List<AuditLog>> getOrderTimeline(@PathVariable UUID orderId) {
        return ApiResponse.ok(
                pointInTimeQueryService.getTimeline(orderId),
                "Lịch sử sự kiện đơn hàng " + orderId
        );
    }

    /**
     * Point-in-time query: đơn hàng trông như thế nào tại thời điểm T?
     * Thuật toán:
     *   1. Lấy snapshot gần nhất trước T
     *   2. Apply các audit log từ snapshot đến T (merge payload)
     *   3. Trả về state reconstruct dạng Map<String, Object>
     *
     * Ví dụ:
     *   GET /api/v1/admin/audit/orders/3f2a1b.../state-at?t=2025-05-26T10:00:00Z
     */
    @GetMapping("/orders/{orderId}/state-at")
    public ApiResponse<Map<String, Object>> getOrderStateAt(
            @PathVariable UUID orderId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant t) {
        Map<String, Object> state = pointInTimeQueryService.getStateAt("Order", orderId, t);
        return ApiResponse.ok(state, "State của đơn hàng " + orderId + " tại " + t);
    }

    /**
     * Cross-service trace: toàn bộ sự kiện trong cùng 1 OpenTelemetry traceId.
     * correlationId = traceId từ header "traceparent" (propagate tự động qua Micrometer Tracing).
     *
     * Kết quả điển hình (1 luồng đặt hàng):
     *   order-service  | ORDER_CREATED          | 10:00 | {orderId, status:PENDING, ...}
     *   payment-service| PAYMENT_CREATED        | 10:00 | {paymentId, method:VNPAY, ...}
     *   order-service  | PAYMENT_COMPLETED_RECV | 10:05 | {orderId, newStatus:CONFIRMED}
     *
     * Ví dụ:
     *   GET /api/v1/admin/audit/trace/4bf92f3577b34da6a3ce929d0e0e4736
     */
    @GetMapping("/trace/{correlationId}")
    public ApiResponse<List<AuditLog>> traceByCorrelation(@PathVariable String correlationId) {
        return ApiResponse.ok(
                pointInTimeQueryService.getTimelineByCorrelation(correlationId),
                "Cross-service trace cho correlationId: " + correlationId
        );
    }
}
