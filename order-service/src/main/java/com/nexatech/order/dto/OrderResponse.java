package com.nexatech.order.dto;

import com.nexatech.order.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OrderResponse {

    private UUID id;
    private String orderNumber;
    private UUID customerId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private BigDecimal shippingFee;
    private BigDecimal discount;
    private String shippingAddress;
    private String note;
    private List<OrderItemResponse> items;
    private Instant createdAt;

    @Data
    @Builder
    public static class OrderItemResponse {
        private UUID id;
        private String productId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}
