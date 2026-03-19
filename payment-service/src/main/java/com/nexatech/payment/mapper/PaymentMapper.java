package com.nexatech.payment.mapper;

import com.nexatech.payment.dto.PaymentResponse;
import com.nexatech.payment.model.Payment;
import org.springframework.stereotype.Component;

/**
 * Mapper chuyển đổi Payment entity → PaymentResponse DTO.
 */
@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
