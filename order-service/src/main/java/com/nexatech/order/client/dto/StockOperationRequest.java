package com.nexatech.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request gửi lên inventory-service để check / reserve / release tồn kho.
 * Cấu trúc phải khớp với StockCheckRequest của inventory-service (theo API contract).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockOperationRequest {

    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String productId;
        private int quantity;
    }
}
