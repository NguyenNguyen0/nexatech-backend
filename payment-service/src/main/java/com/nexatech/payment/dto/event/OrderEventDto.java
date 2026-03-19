package com.nexatech.payment.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO dùng để deserialize OrderEvent nhận từ Kafka topic "order.events".
 * Dùng String cho status để tránh coupling chặt với enum của order-service.
 * @JsonIgnoreProperties: bỏ qua các field không dùng đến trong payment-service.
 */
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
