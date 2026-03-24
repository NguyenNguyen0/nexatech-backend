package com.nexatech.inventory.event;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class InventoryEvent {

    private UUID eventId;
    private String eventType;
    private Instant timestamp;
    private String productId;
    private String productName;
    private int quantity;
    private int reorderLevel;
    private String warehouseLocation;
}
