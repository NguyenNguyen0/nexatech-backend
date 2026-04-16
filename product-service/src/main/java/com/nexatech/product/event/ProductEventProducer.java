package com.nexatech.product.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishProductEvent(String productId, Object event) {
        kafkaTemplate.send("product.events", productId, event);
    }

    public void publishReviewEvent(String productId, Object event) {
        kafkaTemplate.send("review.events", productId, event);
    }
}
