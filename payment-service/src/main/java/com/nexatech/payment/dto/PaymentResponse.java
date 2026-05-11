package com.nexatech.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexatech.payment.enums.PaymentMethod;
import com.nexatech.payment.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {

    private UUID id;
    private UUID orderId;
    private UUID customerId;
    private BigDecimal amount;
    private PaymentMethod method;
    private PaymentStatus status;
    private String transactionId;
    private String failureReason;
    private Instant createdAt;
    private Instant completedAt;
}
