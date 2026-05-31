package com.nexatech.product.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryStockResponse {

    private Integer status;
    private String message;
    private StockData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StockData {
        private String productId;
        private int quantity;
        private int reservedQuantity;
        private int availableQuantity;
        private int reorderLevel;
        private String warehouseLocation;
    }
}
