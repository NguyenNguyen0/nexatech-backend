package com.nexatech.order.dto;

import com.nexatech.order.enums.OrderStatus;
import com.nexatech.order.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private UUID id;
    private String orderNumber;
    private UUID customerId;
    private OrderStatus status;

    // Guest / buyer contact info
    private String guestName;
    private String guestEmail;
    private String guestPhone;
    private BigDecimal totalAmount;
    private BigDecimal shippingFee;
    private BigDecimal discount;
    private String shippingAddress;
    private String shippingMethod;
    private PaymentMethod paymentMethod;
    private String note;
    private List<OrderItemResponse> items;
    private Instant createdAt;

    // Thông tin theo dõi vận chuyển
    private String trackingNumber;
    private String carrierName;
    private Instant estimatedDelivery;
    private Instant shippedAt;
    private Instant deliveredAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private UUID id;
        private String productId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}
