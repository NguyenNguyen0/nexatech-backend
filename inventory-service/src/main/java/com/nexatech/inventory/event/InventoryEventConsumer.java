package com.nexatech.inventory.event;

import com.nexatech.inventory.dto.StockCheckRequest;
import com.nexatech.inventory.exception.BadRequestException;
import com.nexatech.inventory.exception.ResourceNotFoundException;
import com.nexatech.inventory.model.ProcessedEvent;
import com.nexatech.inventory.repository.ProcessedEventRepository;
import com.nexatech.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private static final String CONSUMER_NAME = "inventory-service:InventoryEventConsumer";

    private final InventoryService inventoryService;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    @KafkaListener(
            topics = "order.events",
            groupId = "inventory-service-group",
            containerFactory = "orderEventKafkaListenerContainerFactory")
    public void handleOrderEvent(
            OrderEventDto event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        if (event == null) {
            return;
        }

        UUID eventId = event.getEventId();
        if (eventId == null) {
            log.warn("OrderEvent is missing eventId, inventory deduplication skipped: type={}", event.getEventType());
        } else if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
            log.info("Duplicate OrderEvent skipped by inventory-service: eventId={} type={}",
                    eventId, event.getEventType());
            return;
        }

        try {
            String eventType = event.getEventType();
            log.info("Received order event type={} orderId={}", eventType, event.getOrderId());

            boolean acted = true;
            switch (eventType) {
                case "ORDER_CANCELLED" -> handleOrderCancelled(event);
                case "ORDER_OUT_OF_STOCK", "ORDER_SHIPPING_FAILED", "ORDER_REFUND_PENDING" -> handleOrderCompensation(event);
                case "ORDER_DELIVERED" -> handleOrderDelivered(event);
                case "ORDER_STATUS_UPDATED" -> {
                    if ("CONFIRMED".equals(event.getStatus())) {
                        handleOrderConfirmed(event);
                    } else if ("DELIVERED".equals(event.getStatus())) {
                        handleOrderDelivered(event);
                    } else {
                        acted = false; // other statuses (PROCESSING, SHIPPED, etc.) are intentionally ignored
                    }
                }
                case "ORDER_RETURN_APPROVED" -> handleOrderReturnApproved(event);
                default -> {
                    acted = false;
                    log.debug("Ignoring unsupported order event type={}", eventType);
                }
            }

            // Only persist dedup record if we actually did something.
            // Skipping on ignored events prevents stale records from blocking future
            // code paths after a service restart with new logic.
            if (acted && eventId != null) {
                processedEventRepository.save(ProcessedEvent.builder()
                        .eventId(eventId)
                        .eventType(eventType != null ? eventType : "UNKNOWN")
                        .consumerName(CONSUMER_NAME)
                        .aggregateId(event.getOrderId() != null ? event.getOrderId().toString() : null)
                        .build());
            }
        } catch (Exception e) {
            log.error("[KAFKA][{}] partition={} offset={} key={} processError type={} orderId={} error={}",
                    topic,
                    partition,
                    offset,
                    key,
                    event.getEventType(),
                    event.getOrderId(),
                    e.getMessage(),
                    e);
            throw new RuntimeException("Failed to process order event", e);
        }
    }

    private void handleOrderCancelled(OrderEventDto event) {
        StockCheckRequest request = buildStockCheckRequest(event);
        try {
            inventoryService.releaseOrRestock(
                    request,
                    "ORDER:" + event.getOrderId(),
                    "Release reserved stock for cancelled order"
            );
        } catch (BadRequestException | ResourceNotFoundException e) {
            log.warn("Skip cancelled-order stock release/restock for orderId={} because stock is already unavailable or released: {}",
                    event.getOrderId(), e.getMessage());
        }
    }

    private void handleOrderCompensation(OrderEventDto event) {
        StockCheckRequest request = buildStockCheckRequest(event);
        try {
            inventoryService.releaseOrRestock(
                    request,
                    "ORDER:" + event.getOrderId(),
                    "Release reserved stock for compensated order"
            );
        } catch (BadRequestException | ResourceNotFoundException e) {
            log.warn("Skip compensation stock release/restock for orderId={} because stock is already unavailable or released: {}",
                    event.getOrderId(), e.getMessage());
        }
    }

    private void handleOrderConfirmed(OrderEventDto event) {
        StockCheckRequest request = buildStockCheckRequest(event);
        try {
            inventoryService.finalizeSale(request, "ORDER:" + event.getOrderId());
        } catch (BadRequestException | ResourceNotFoundException e) {
            log.warn("Inventory finalization failed for confirmed orderId={}: {}",
                    event.getOrderId(), e.getMessage());
            inventoryService.publishOrderOutOfStockEvent(event, e.getMessage());
        }
    }

    private void handleOrderDelivered(OrderEventDto event) {
        StockCheckRequest request = buildStockCheckRequest(event);
        try {
            inventoryService.finalizeSale(request, "ORDER:" + event.getOrderId());
        } catch (BadRequestException | ResourceNotFoundException e) {
            log.warn("Inventory finalization failed for delivered orderId={}: {}",
                    event.getOrderId(), e.getMessage());
            inventoryService.publishOrderOutOfStockEvent(event, e.getMessage());
        }
    }

    private void handleOrderReturnApproved(OrderEventDto event) {
        StockCheckRequest request = buildStockCheckRequest(event);
        inventoryService.restockReturn(request, "RETURN:" + event.getOrderId());
    }

    private StockCheckRequest buildStockCheckRequest(OrderEventDto event) {
        if (event.getItems() == null || event.getItems().isEmpty()) {
            throw new BadRequestException("Order event does not contain items");
        }

        List<StockCheckRequest.Item> items = event.getItems().stream().map(source -> {
            StockCheckRequest.Item item = new StockCheckRequest.Item();
            item.setProductId(source.getProductId());
            item.setQuantity(source.getQuantity());
            return item;
        }).toList();

        StockCheckRequest request = new StockCheckRequest();
        request.setItems(items);
        return request;
    }
}
