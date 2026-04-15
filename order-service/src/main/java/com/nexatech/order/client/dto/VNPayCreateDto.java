package com.nexatech.order.client.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class VNPayCreateDto {

    private UUID orderId;
    private BigDecimal amount;
    private String orderInfo;
}
