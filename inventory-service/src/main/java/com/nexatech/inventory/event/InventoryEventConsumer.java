package com.nexatech.inventory.event;

import com.nexatech.inventory.dto.StockCheckRequest;
import com.nexatech.inventory.exception.BadRequestException;
import com.nexatech.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
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
    public void handleOrderEvent(OrderEventDto event) {
        if (event == null) {
            return;
        }

        try {
            String eventType = event.getEventType();
            log.info("Received order event type={} orderId={}", eventType, event.getOrderId());

            switch (eventType) {
                case "ORDER_CANCELLED" -> handleOrderCancelled(event);
                case "ORDER_DELIVERED" -> handleOrderDelivered(event);
                default -> log.debug("Ignoring unsupported order event type={}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process order event type={} orderId={} error={}",
                    event.getEventType(), event.getOrderId(), e.getMessage(), e);
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
