package com.nexatech.payment.controller;

import com.nexatech.payment.dto.PaymentResponse;
import com.nexatech.payment.dto.PaymentStatsResponse;
import com.nexatech.payment.dto.response.ApiResponse;
import com.nexatech.payment.dto.response.PageResponse;
import com.nexatech.payment.enums.PaymentStatus;
import com.nexatech.payment.model.AuditLog;
import com.nexatech.payment.service.PaymentService;
import com.nexatech.payment.service.PaymentStatsService;
import com.nexatech.payment.service.PointInTimeQueryService;
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
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
public class PaymentAdminController {

    private final PaymentService paymentService;
    private final PaymentStatsService paymentStatsService;
    private final PointInTimeQueryService pointInTimeQueryService;

    @GetMapping
    public ApiResponse<PageResponse<PaymentResponse>> getAllPayments(
            @RequestParam(required = false) PaymentStatus status,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(paymentService.getAllPayments(status, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getPayment(@PathVariable UUID id) {
        return ApiResponse.ok(paymentService.getPayment(id));
    }

    @GetMapping("/{id}/timeline")
    public ApiResponse<List<AuditLog>> getTimeline(@PathVariable UUID id) {
        return ApiResponse.ok(pointInTimeQueryService.getTimeline(id));
    }

    @GetMapping("/{id}/state-at")
    public ApiResponse<Map<String, Object>> getStateAt(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant t) {
        return ApiResponse.ok(pointInTimeQueryService.getStateAt("Payment", id, t));
    }

    @GetMapping("/trace/{correlationId}")
    public ApiResponse<List<AuditLog>> traceByCorrelation(@PathVariable String correlationId) {
        return ApiResponse.ok(pointInTimeQueryService.getTimelineByCorrelation(correlationId));
    }

    @GetMapping("/stats")
    public ApiResponse<PaymentStatsResponse> getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate resolvedFrom = (from != null) ? from : LocalDate.now().minusDays(30);
        LocalDate resolvedTo = (to != null) ? to : LocalDate.now();
        return ApiResponse.ok(paymentStatsService.getStats(resolvedFrom, resolvedTo));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<PaymentResponse> refund(@PathVariable UUID id) {
        return ApiResponse.ok(paymentService.refund(id));
    }
}
