package com.nexatech.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.payment.dto.response.ApiResponse;
import com.nexatech.payment.dto.response.PageResponse;
import com.nexatech.payment.dto.CreatePaymentRequest;
import com.nexatech.payment.dto.PaymentResponse;
import com.nexatech.payment.exception.ResourceNotFoundException;
import com.nexatech.payment.service.IdempotencyService;
import com.nexatech.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    /**
     * Tạo giao dịch thanh toán mới.
     * Header Idempotency-Key (tùy chọn): nếu cung cấp, đảm bảo retry an toàn không tạo duplicate.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentResponse> createPayment(
            @RequestHeader(value = "X-User-Id", required = false) UUID customerId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        // --- Idempotency check ---
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<String> cached = idempotencyService.checkAndGetCached(idempotencyKey);
            if (cached.isPresent()) {
                try {
                    PaymentResponse cachedResponse = objectMapper.readValue(cached.get(), PaymentResponse.class);
                    return ApiResponse.ok(cachedResponse);
                } catch (Exception e) {
                    log.warn("Không thể deserialize cached idempotency response: key={}", idempotencyKey);
                }
            }
            idempotencyService.markProcessing(idempotencyKey, request.getOrderId());
        }

        // --- Xử lý nghiệp vụ ---
        PaymentResponse response;
        try {
            response = paymentService.createPayment(customerId, request);
        } catch (RuntimeException ex) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyService.markFailed(idempotencyKey);
            }
            throw ex;
        }

        // --- Lưu response vào idempotency cache ---
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.markCompleted(idempotencyKey, response.getId(), response);
        }

        return ApiResponse.ok(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getPayment(@PathVariable UUID id) {
        return ApiResponse.ok(paymentService.getPayment(id));
    }

    @GetMapping("/order/{orderId}")
    public ApiResponse<PaymentResponse> getPaymentByOrder(@PathVariable UUID orderId) {
        try {
            return ApiResponse.ok(paymentService.getPaymentByOrder(orderId));
        } catch (ResourceNotFoundException e) {
            return ApiResponse.ok(null, "Order has no payment yet");
        }
    }

    @GetMapping("/my")
    public ApiResponse<PageResponse<PaymentResponse>> getMyPayments(
            @RequestHeader("X-User-Id") UUID customerId,
            @RequestParam(required = false) com.nexatech.payment.enums.PaymentStatus status,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(paymentService.getMyPayments(customerId, status, pageable)));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<PaymentResponse> refund(@PathVariable UUID id) {
        return ApiResponse.ok(paymentService.refund(id));
    }
}

