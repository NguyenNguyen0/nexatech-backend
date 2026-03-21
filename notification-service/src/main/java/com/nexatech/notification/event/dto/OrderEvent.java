package com.nexatech.notification.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
    private String customerEmail;
    private String status;
    private BigDecimal totalAmount;
}
