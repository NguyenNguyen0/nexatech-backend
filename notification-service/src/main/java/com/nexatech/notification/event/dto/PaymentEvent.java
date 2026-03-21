package com.nexatech.notification.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Published by payment-service to "payment.events" topic.
 * Relevant statuses: COMPLETED, REFUNDED, FAILED
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentEvent extends BaseEvent {

    private String paymentId;
    private String orderId;
    private String customerId;
    private String customerEmail;
    private BigDecimal amount;
    private String status;
}
