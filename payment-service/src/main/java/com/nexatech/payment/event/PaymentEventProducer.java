package com.nexatech.payment.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public void publishPaymentEvent(PaymentEvent event) {
        kafkaTemplate.send("payment.events", event.getOrderId().toString(), event);
    }
}
