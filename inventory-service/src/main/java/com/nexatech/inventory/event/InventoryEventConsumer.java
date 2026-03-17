package com.nexatech.inventory.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    @KafkaListener(topics = "order.events", groupId = "inventory-service-group")
    public void handleOrderEvent(String message) {
        log.info("Received order event: {}", message);
    }
}
