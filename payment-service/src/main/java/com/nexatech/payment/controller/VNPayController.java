package com.nexatech.payment.controller;

import com.nexatech.payment.dto.VNPayPaymentRequest;
import com.nexatech.payment.dto.VNPayResponse;
import com.nexatech.payment.service.VNPayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller tích hợp cổng thanh toán VNPay.
 *
 * Endpoints:
 *   POST /api/v1/payments/vnpay/create  — Tạo URL thanh toán (cần X-User-Id)
 *   GET  /api/v1/payments/vnpay/ipn     — IPN callback server-to-server từ VNPay
 *   GET  /api/v1/payments/vnpay/return  — Return URL (redirect trình duyệt về)
 *
 * Demo UI: http://localhost:8086/vnpay-demo.html
 */
@RestController
@RequestMapping("/api/v1/payments/vnpay")
@RequiredArgsConstructor
public class VNPayController {

    private final VNPayService vnPayService;

    /**
     * Tạo URL thanh toán VNPay.
     * Response: { paymentId, paymentUrl } — redirect client tới paymentUrl.
     */
    @PostMapping("/create")
    public ResponseEntity<VNPayResponse> createPayment(
            @RequestHeader("X-User-Id") UUID customerId,
            @Valid @RequestBody VNPayPaymentRequest request) {
        return ResponseEntity.ok(vnPayService.createPaymentUrl(customerId, request));
    }

    /**
     * IPN (Instant Payment Notification) — VNPay gọi server-to-server sau khi thanh toán.
     * Không cần xác thực người dùng (không có X-User-Id).
     * Phải trả về {"RspCode":"00","Message":"Confirm Success"} trong vòng 5 giây.
     */
    @GetMapping("/ipn")
    public ResponseEntity<Map<String, String>> handleIpn(
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(vnPayService.processIpn(params));
    }

    /**
     * Return URL — VNPay redirect trình duyệt về đây sau khi thanh toán xong.
     * Trả về JSON để frontend kiểm tra kết quả.
     */
    @GetMapping("/return")
    public ResponseEntity<Map<String, String>> handleReturn(
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(vnPayService.processReturn(params));
    }
}
