package com.nexatech.notification.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Published by order-service to "order.events" topic.
 * Relevant statuses: PENDING (order placed), CANCELLED, DELIVERED
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent extends BaseEvent {

    private String orderId;
    private String orderNumber;
    private String customerId;
    private String recipientUserId;
    private String recipientEmail;
    private String customerEmail;
    private String status;
    private String paymentMethod;
    private BigDecimal totalAmount;
    private BigDecimal shippingFee;
    private BigDecimal discount;
    private String shippingAddress;
    private String shippingMethod;
    private String carrierName;
    private String trackingNumber;
    private String guestName;
    private String guestPhone;
    private String note;
    private List<OrderItemEvent> items;

    @Data
    @NoArgsConstructor
    public static class OrderItemEvent {
        private String productId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}
