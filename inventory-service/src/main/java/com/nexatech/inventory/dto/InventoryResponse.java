package com.nexatech.inventory.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class InventoryResponse {

    private UUID id;
    private String productId;
    private int quantity;
    private int reservedQuantity;
    private int availableQuantity;
    private int reorderLevel;
    private String warehouseLocation;
}
