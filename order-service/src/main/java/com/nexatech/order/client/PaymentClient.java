package com.nexatech.order.client;

import com.nexatech.order.client.dto.CreatePaymentDto;
import com.nexatech.order.client.dto.PaymentInfo;
import com.nexatech.order.client.dto.VNPayCreateDto;
import com.nexatech.order.client.dto.VNPayInfo;
import com.nexatech.order.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "payment-service", url = "${payment-service.url:}", path = "/api/v1/payments", fallback = PaymentClientFallback.class)
public interface PaymentClient {

    @PostMapping
    ApiResponse<PaymentInfo> createPayment(
            @RequestHeader("X-User-Id") UUID customerId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreatePaymentDto request);

    @PostMapping("/vnpay/create")
    VNPayInfo createVNPayPayment(
            @RequestHeader("X-User-Id") UUID customerId,
            @RequestBody VNPayCreateDto request);
}
