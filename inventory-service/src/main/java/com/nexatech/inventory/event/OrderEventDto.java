package com.nexatech.inventory.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEventDto {

    private UUID eventId;
    private String eventType;
    private Instant timestamp;

    private UUID orderId;
    private String orderNumber;
    private UUID customerId;
    private String status;
    private List<Item> items;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String productId;
        private int quantity;
    }
}
