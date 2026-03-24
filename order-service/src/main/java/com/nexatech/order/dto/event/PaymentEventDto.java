package com.nexatech.order.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO nhẹ để deserialize PaymentEvent nhận từ Kafka topic "payment.events".
 * Dùng String cho status/method để tránh coupling với enum của payment-service.
 * @JsonIgnoreProperties: bỏ qua field không dùng trong order-service.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentEventDto {

    private UUID eventId;
    private String eventType;
    private Instant timestamp;

    private UUID paymentId;
    private UUID orderId;
    private UUID customerId;

    /** PENDING / PROCESSING / COMPLETED / FAILED / REFUNDED */
    private String status;

    private BigDecimal amount;
}
