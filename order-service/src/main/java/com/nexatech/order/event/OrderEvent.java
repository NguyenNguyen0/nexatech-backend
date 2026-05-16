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
    private OrderStatus status;
    private String paymentMethod;
    private BigDecimal totalAmount;
    private String shippingAddress;
    private String guestName;
    private String guestPhone;
    private List<OrderItemEvent> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemEvent {
        private String productId;
        private int quantity;
    }
}
