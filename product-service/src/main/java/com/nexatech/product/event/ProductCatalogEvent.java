package com.nexatech.product.event;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ProductCatalogEvent {

    private UUID eventId;
    private String eventType;
    private Instant timestamp;
    private String productId;
    private String name;
    private String categoryId;
    private boolean active;
}
