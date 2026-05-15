package com.nexatech.shipping.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.shipping.dto.event.OrderEventDto;
import com.nexatech.shipping.entity.ProcessedEvent;
import com.nexatech.shipping.repository.ProcessedEventRepository;
import com.nexatech.shipping.service.AuditLogService;
import com.nexatech.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private static final String CONSUMER_NAME = "shipping-service:OrderEventConsumer";

    private final ShippingService shippingService;
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final AuditLogService auditLogService;

    @Transactional
    @KafkaListener(topics = "order.events", groupId = "shipping-service-group")
    public void handleOrderEvent(String message) {
        OrderEventDto event;
        try {
            event = objectMapper.readValue(message, OrderEventDto.class);
        } catch (Exception e) {
            log.error("Unable to parse OrderEvent for shipping: {} | message={}", e.getMessage(), message);
            return;
        }

        UUID eventId = event.getEventId();
        if (eventId == null) {
            log.warn("OrderEvent is missing eventId, deduplication skipped: type={}", event.getEventType());
        } else if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
            log.info("Duplicate OrderEvent skipped by shipping-service: eventId={} type={}",
                    eventId, event.getEventType());
            return;
        }

        if (event.getOrderId() == null) {
            return;
        }

        try {
            switch (event.getEventType() != null ? event.getEventType() : "") {
                case "ORDER_CANCELLED" -> {
                    shippingService.cancelShipmentByOrderIdIfPresent(event.getOrderId().toString(), CONSUMER_NAME);
                    auditLogService.log("ORDER_CANCELLED_RECEIVED", "Shipment", event.getOrderId().toString(),
                            "cancel shipment if present", CONSUMER_NAME);
                }
                case "ORDER_STATUS_UPDATED" -> {
                    if ("CONFIRMED".equals(event.getStatus())) {
                        auditLogService.log("ORDER_CONFIRMED_RECEIVED", "Shipment", event.getOrderId().toString(),
                                "order ready for shipment creation", CONSUMER_NAME);
                    }
                }
                default -> log.debug("OrderEvent ignored by shipping-service: type={}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Unable to process OrderEvent in shipping-service: type={} orderId={} error={}",
                    event.getEventType(), event.getOrderId(), e.getMessage());
            return;
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
