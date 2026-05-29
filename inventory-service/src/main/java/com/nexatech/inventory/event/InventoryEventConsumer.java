package com.nexatech.inventory.event;

import com.nexatech.inventory.dto.StockCheckRequest;
import com.nexatech.inventory.exception.BadRequestException;
import com.nexatech.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final InventoryService inventoryService;

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

        try {
            String eventType = event.getEventType();
            log.info("Received order event type={} orderId={}", eventType, event.getOrderId());

            switch (eventType) {
                case "ORDER_CANCELLED" -> handleOrderCancelled(event);
                case "ORDER_DELIVERED" -> handleOrderDelivered(event);
                case "ORDER_STATUS_UPDATED" -> {
                    if ("DELIVERED".equals(event.getStatus())) {
                        handleOrderDelivered(event);
                    }
                }
                case "ORDER_RETURN_APPROVED" -> handleOrderReturnApproved(event);
                default -> log.debug("Ignoring unsupported order event type={}", eventType);
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
        inventoryService.releaseStock(
                request,
                "ORDER:" + event.getOrderId(),
                "Release reserved stock for cancelled order"
        );
    }

    private void handleOrderDelivered(OrderEventDto event) {
        StockCheckRequest request = buildStockCheckRequest(event);
        inventoryService.finalizeSale(request, "ORDER:" + event.getOrderId());
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
