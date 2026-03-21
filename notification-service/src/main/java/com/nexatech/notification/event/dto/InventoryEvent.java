package com.nexatech.notification.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Published by inventory-service to "inventory.events" topic.
 * eventType = "LOW_STOCK" when quantity <= reorderLevel.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryEvent extends BaseEvent {

    private String productId;
    private String productName;
    private int quantity;
    private int reorderLevel;
    private String warehouseLocation;
}
