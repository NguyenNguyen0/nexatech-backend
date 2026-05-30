package com.nexatech.order.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryEventDto {

    private UUID eventId;
    private String eventType;
    private Instant timestamp;
    private UUID orderId;
    private String orderNumber;
    private String productId;
    private String reason;
    private String message;
}
