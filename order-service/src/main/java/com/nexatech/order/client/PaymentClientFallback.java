package com.nexatech.order.client;

import com.nexatech.order.client.dto.CreatePaymentDto;
import com.nexatech.order.client.dto.PaymentInfo;
import com.nexatech.order.client.dto.VNPayCreateDto;
import com.nexatech.order.client.dto.VNPayInfo;
import com.nexatech.order.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class PaymentClientFallback implements PaymentClient {

    @Override
    public ApiResponse<PaymentInfo> createPayment(UUID customerId, String idempotencyKey, CreatePaymentDto request) {
        log.warn("Fallback: payment-service createPayment không khả dụng, orderId={}", request.getOrderId());
        return ApiResponse.<PaymentInfo>builder().success(false).message("Payment service unavailable").build();
    }

    @Override
    public VNPayInfo createVNPayPayment(UUID customerId, VNPayCreateDto request) {
        log.warn("Fallback: payment-service createVNPay không khả dụng, orderId={}", request.getOrderId());
        return null;
    }
}
