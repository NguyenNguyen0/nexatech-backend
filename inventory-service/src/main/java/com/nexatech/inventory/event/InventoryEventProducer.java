package com.nexatech.inventory.event;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryEventProducer {

    @Qualifier("inventoryEventKafkaTemplate")
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishInventoryEvent(String productId, Object event) {
        kafkaTemplate.send("inventory.events", productId, event);
    }
}
