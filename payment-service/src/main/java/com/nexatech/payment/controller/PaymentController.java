package com.nexatech.payment.controller;

import com.nexatech.payment.dto.response.ApiResponse;
import com.nexatech.payment.dto.response.PageResponse;
import com.nexatech.payment.dto.CreatePaymentRequest;
import com.nexatech.payment.dto.PaymentResponse;
import com.nexatech.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentResponse> createPayment(
            @RequestHeader("X-User-Id") UUID customerId,
            @Valid @RequestBody CreatePaymentRequest request) {
        return ApiResponse.ok(paymentService.createPayment(customerId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getPayment(@PathVariable UUID id) {
        return ApiResponse.ok(paymentService.getPayment(id));
    }

    @GetMapping("/order/{orderId}")
    public ApiResponse<PaymentResponse> getPaymentByOrder(@PathVariable UUID orderId) {
        return ApiResponse.ok(paymentService.getPaymentByOrder(orderId));
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
