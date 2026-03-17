package com.nexatech.notification.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationEventConsumer {

    @KafkaListener(topics = "user.events", groupId = "notification-user-group")
    public void handleUserEvent(String message) {
        log.info("Received user event: {}", message);
    }

    @KafkaListener(topics = "order.events", groupId = "notification-order-group")
    public void handleOrderEvent(String message) {
        log.info("Received order event: {}", message);
    }

    @KafkaListener(topics = "payment.events", groupId = "notification-payment-group")
    public void handlePaymentEvent(String message) {
        log.info("Received payment event: {}", message);
    }

    @KafkaListener(topics = "inventory.events", groupId = "notification-inventory-group")
    public void handleInventoryEvent(String message) {
        log.info("Received inventory event: {}", message);
    }
}
