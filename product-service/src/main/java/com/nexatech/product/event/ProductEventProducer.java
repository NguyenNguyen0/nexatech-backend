package com.nexatech.product.event;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${product.events.enabled:true}")
    private boolean eventsEnabled;

    public void publishProductEvent(String productId, Object event) {
        if (!eventsEnabled) {
            return;
        }
        kafkaTemplate.send("product.events", productId, event);
    }

    public void publishReviewEvent(String productId, Object event) {
        if (!eventsEnabled) {
            return;
        }
        kafkaTemplate.send("review.events", productId, event);
    }
}
