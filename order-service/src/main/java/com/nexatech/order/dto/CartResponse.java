package com.nexatech.order.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class CartResponse {

    private List<CartItemResponse> items;
    private int totalItems;
    private BigDecimal totalAmount;
    private Instant updatedAt;

    @Data
    @Builder
    public static class CartItemResponse {
        private String productId;
        private String productName;
        private String imageUrl;
        private BigDecimal unitPrice;
        private int quantity;
        private BigDecimal subtotal;
    }
}
