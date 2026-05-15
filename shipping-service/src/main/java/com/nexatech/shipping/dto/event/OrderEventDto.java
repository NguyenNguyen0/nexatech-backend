package com.nexatech.shipping.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEventDto {

    private UUID eventId;
    private String eventType;
    private Instant timestamp;
    private UUID orderId;
    private String orderNumber;
    private UUID customerId;
    private String status;
    private BigDecimal totalAmount;
}
