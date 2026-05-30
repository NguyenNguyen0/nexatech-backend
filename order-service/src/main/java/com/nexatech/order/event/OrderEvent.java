package com.nexatech.order.event;

import com.nexatech.order.event.BaseEvent;
import com.nexatech.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrderEvent extends BaseEvent {

    private UUID orderId;
    private String orderNumber;
    private UUID customerId;
    private UUID recipientUserId;
    private String recipientEmail;
    private OrderStatus status;
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
    private String compensationReason;
    private String refundBankName;
    private String refundAccountNumber;
    private String refundAccountHolder;
    private List<OrderItemEvent> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemEvent {
        private String productId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}
