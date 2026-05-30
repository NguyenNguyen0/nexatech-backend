package com.nexatech.product.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.product.service.ProductCacheInvalidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryCacheEventConsumer {

    private final ObjectMapper objectMapper;
    private final ProductCacheInvalidationService cacheInvalidationService;

    @KafkaListener(
            topics = "inventory.events",
            groupId = "product-service-cache-group",
            containerFactory = "inventoryCacheKafkaListenerContainerFactory")
    public void handleInventoryEvent(String message) {
        InventoryEventDto event;
        try {
            event = objectMapper.readValue(message, InventoryEventDto.class);
        } catch (Exception e) {
            log.warn("Unable to parse inventory event for product cache invalidation: {}", e.getMessage());
            return;
        }

        String eventType = event.getEventType();
        if (eventType == null || !eventType.startsWith("INVENTORY_") && !"LOW_STOCK".equals(eventType)) {
            return;
        }

        cacheInvalidationService.evictProductCaches(event.getProductId(), eventType);
    }
}
