package com.nexatech.product.event;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class InventoryEventDto {

    private UUID eventId;
    private String eventType;
    private Instant timestamp;
    private UUID orderId;
    private String orderNumber;
    private String productId;
    private String productName;
    private Integer quantity;
    private Integer reorderLevel;
    private String warehouseLocation;
    private String reason;
    private String message;
}
