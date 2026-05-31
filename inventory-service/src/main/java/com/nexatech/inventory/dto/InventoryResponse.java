package com.nexatech.inventory.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {

    private UUID id;
    private String productId;
    private String productName;
    private String productSlug;
    private String productThumbnail;
    private String sku;
    private int quantity;
    private int reservedQuantity;
    private int availableQuantity;
    private int reorderLevel;
    private String status;
    private String warehouseLocation;
}
