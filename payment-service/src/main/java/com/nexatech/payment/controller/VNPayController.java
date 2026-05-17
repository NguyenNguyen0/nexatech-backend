package com.nexatech.payment.controller;

import com.nexatech.payment.dto.VNPayPaymentRequest;
import com.nexatech.payment.dto.VNPayResponse;
import com.nexatech.payment.service.VNPayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

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

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Tạo URL thanh toán VNPay.
     * Response: { paymentId, paymentUrl } — redirect client tới paymentUrl.
     */
    @PostMapping("/create")
    public ResponseEntity<VNPayResponse> createPayment(
            @RequestHeader(value = "X-User-Id", required = false) UUID customerId,
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
     * Redirect trình duyệt tới frontend (trang success hoặc failed).
     */
    @GetMapping("/return")
    public ResponseEntity<Void> handleReturn(
            @RequestParam Map<String, String> params) {
        Map<String, String> result = vnPayService.processReturn(params);
        boolean success = "true".equals(result.get("success"));
        String txnRef = params.get("vnp_TxnRef");

        // Look up orderId from payment record using txnRef
        String orderId = vnPayService.getOrderIdByTxnRef(txnRef);

        java.net.URI redirectUri;
        if (success && orderId != null) {
            // Use UriComponentsBuilder to safely encode vnp_OrderInfo (may contain spaces)
            redirectUri = UriComponentsBuilder.fromUriString(frontendUrl)
                    .path("/cart/checkout/success")
                    .queryParam("orderId", orderId)
                    .queryParam("orderNumber", result.getOrDefault("vnp_OrderInfo", ""))
                    .queryParam("paymentMethod", "VNPAY")
                    .build()
                    .encode()
                    .toUri();
        } else {
            redirectUri = UriComponentsBuilder.fromUriString(frontendUrl)
                    .path("/cart")
                    .queryParam("paymentFailed", "true")
                    .build()
                    .toUri();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(redirectUri);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
