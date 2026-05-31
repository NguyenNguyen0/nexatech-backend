package com.nexatech.order.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO để deserialize ShippingEvent nhận từ Kafka topic "shipping.events".
 * Dùng String cho status/provider để tránh coupling với enum của shipping-service.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShippingEventDto {

    private UUID eventId;
    private String eventType;
    private Instant timestamp;

    private UUID shipmentId;

    /** orderId từ shipping-service được lưu dạng String, parse về UUID khi cần */
    private String orderId;

    /** Enum name: MOCK | GHN */
    private String provider;

    private String trackingCode;

    /** Enum name: PENDING_PICKUP | PICKED_UP | IN_TRANSIT | DELIVERED | FAILED | RETURNED | CANCELLED */
    private String status;

    private BigDecimal shippingFee;
    private String currency;
    private String failureReason;
}
