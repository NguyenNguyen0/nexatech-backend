package com.nexatech.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.order.dto.event.InventoryEventDto;
import com.nexatech.order.model.ProcessedEvent;
import com.nexatech.order.repository.ProcessedEventRepository;
import com.nexatech.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderInventoryEventConsumer {

    private static final String CONSUMER_NAME = "order-service:OrderInventoryEventConsumer";

    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    @KafkaListener(topics = "inventory.events", groupId = "order-service-group")
    public void handleInventoryEvent(String message) {
        InventoryEventDto event;
        try {
            event = objectMapper.readValue(message, InventoryEventDto.class);
        } catch (Exception e) {
            log.error("Unable to parse InventoryEvent: {} | message={}", e.getMessage(), message);
            throw new RuntimeException("Unable to parse InventoryEvent: " + e.getMessage(), e);
        }

        UUID eventId = event.getEventId();
        if (eventId != null && processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
            log.info("Duplicate InventoryEvent skipped: eventId={} type={}", eventId, event.getEventType());
            return;
        }

        if (event.getOrderId() == null) {
            log.debug("InventoryEvent ignored because it has no orderId: type={}", event.getEventType());
            return;
        }

        try {
            if ("INVENTORY_OUT_OF_STOCK".equals(event.getEventType())
                    || "INVENTORY_FINALIZATION_FAILED".equals(event.getEventType())) {
                orderService.markOutOfStockForCompensation(
                        event.getOrderId(),
                        event.getReason() != null ? event.getReason() : event.getMessage(),
                        CONSUMER_NAME);
            } else {
                log.debug("InventoryEvent ignored by order-service: type={}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Unable to process InventoryEvent type={} orderId={} error={}",
                    event.getEventType(), event.getOrderId(), e.getMessage());
            throw new RuntimeException("Unable to process InventoryEvent type=" + event.getEventType()
                    + " orderId=" + event.getOrderId(), e);
        }

        if (eventId != null) {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(event.getEventType())
                    .consumerName(CONSUMER_NAME)
                    .aggregateId(event.getOrderId().toString())
                    .build());
        }
    }
}
