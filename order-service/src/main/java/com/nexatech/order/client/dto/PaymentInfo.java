package com.nexatech.order.client.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
public class PaymentInfo {

    private UUID id;
    private UUID orderId;
    private BigDecimal amount;
    private String method;
    private String status;
    private String transactionId;
}
