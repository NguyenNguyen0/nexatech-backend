package com.nexatech.order.dto;

import com.nexatech.order.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContinuePaymentResponse {
    private UUID orderId;
    private String orderNumber;
    private UUID paymentId;
    private PaymentMethod paymentMethod;
    private String paymentUrl;
    private Instant expiresAt;
}
