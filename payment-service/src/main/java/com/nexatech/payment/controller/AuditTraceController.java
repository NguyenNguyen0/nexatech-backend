package com.nexatech.payment.controller;

import com.nexatech.payment.dto.response.ApiResponse;
import com.nexatech.payment.model.AuditLog;
import com.nexatech.payment.service.PointInTimeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoint debug/admin để trace audit log và point-in-time query cho Payment.
 *
 * GET /api/v1/admin/audit/payments/{paymentId}/timeline
 *      → Toàn bộ lịch sử sự kiện của 1 giao dịch thanh toán
 *
 * GET /api/v1/admin/audit/payments/{paymentId}/state-at?t=2025-05-26T10:00:00Z
 *      → State của payment tại thời điểm T
 *
 * GET /api/v1/admin/audit/trace/{correlationId}
 *      → Cross-service trace theo OpenTelemetry traceId
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
public class AuditTraceController {

    private final PointInTimeQueryService pointInTimeQueryService;

    /**
     * Lịch sử sự kiện đầy đủ của 1 giao dịch thanh toán.
     *
     * Ví dụ:
     *   GET /api/v1/admin/audit/payments/abc123.../timeline
     */
    @GetMapping("/payments/{paymentId}/timeline")
    public ApiResponse<List<AuditLog>> getPaymentTimeline(@PathVariable UUID paymentId) {
        return ApiResponse.ok(
                pointInTimeQueryService.getTimeline(paymentId),
                "Lịch sử sự kiện payment " + paymentId
        );
    }

    /**
     * Point-in-time query: payment trông như thế nào tại thời điểm T?
     *
     * Ví dụ:
     *   GET /api/v1/admin/audit/payments/abc123.../state-at?t=2025-05-26T10:05:00Z
     */
    @GetMapping("/payments/{paymentId}/state-at")
    public ApiResponse<Map<String, Object>> getPaymentStateAt(
            @PathVariable UUID paymentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant t) {
        Map<String, Object> state = pointInTimeQueryService.getStateAt("Payment", paymentId, t);
        return ApiResponse.ok(state, "State của payment " + paymentId + " tại " + t);
    }

    /**
     * Cross-service trace: xem toàn bộ sự kiện từ một traceId (xuyên order + payment service).
     * Lấy correlationId từ response header "X-Trace-Id" hoặc query DB audit_logs.correlation_id.
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
