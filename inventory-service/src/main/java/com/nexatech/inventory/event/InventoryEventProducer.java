package com.nexatech.inventory.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishInventoryEvent(String productId, Object event) {
        kafkaTemplate.send("inventory.events", productId, event);
    }
}
