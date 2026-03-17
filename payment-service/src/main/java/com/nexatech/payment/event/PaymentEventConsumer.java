package com.nexatech.payment.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    @KafkaListener(topics = "order.events", groupId = "payment-service-group")
    public void handleOrderEvent(String message) {
        log.info("Received order event: {}", message);
    }
}
