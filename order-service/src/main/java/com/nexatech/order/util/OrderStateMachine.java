package com.nexatech.order.util;

import com.nexatech.order.enums.OrderStatus;
import com.nexatech.order.exception.BadRequestException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING,    EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.OUT_OF_STOCK, OrderStatus.SHIPPING_FAILED, OrderStatus.REFUND_PENDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.CONFIRMED,  EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED, OrderStatus.OUT_OF_STOCK, OrderStatus.SHIPPING_FAILED, OrderStatus.REFUND_PENDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED, OrderStatus.OUT_OF_STOCK, OrderStatus.SHIPPING_FAILED, OrderStatus.REFUND_PENDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPED,    EnumSet.of(OrderStatus.DELIVERED, OrderStatus.SHIPPING_FAILED, OrderStatus.REFUND_PENDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED,  EnumSet.of(OrderStatus.COMPLETED, OrderStatus.RETURNED, OrderStatus.OUT_OF_STOCK, OrderStatus.REFUND_PENDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.COMPLETED,  EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED,  EnumSet.of(OrderStatus.REFUND_PENDING, OrderStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(OrderStatus.OUT_OF_STOCK, EnumSet.of(OrderStatus.REFUND_PENDING, OrderStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPING_FAILED, EnumSet.of(OrderStatus.REFUND_PENDING, OrderStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(OrderStatus.REFUND_PENDING, EnumSet.of(OrderStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(OrderStatus.RETURNED,   EnumSet.of(OrderStatus.REFUND_PENDING, OrderStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(OrderStatus.REFUNDED,   EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {}

    public static void validate(OrderStatus current, OrderStatus next) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(next)) {
            throw new BadRequestException(
                    "Không thể chuyển trạng thái đơn hàng từ " + current + " sang " + next);
        }
    }
}
