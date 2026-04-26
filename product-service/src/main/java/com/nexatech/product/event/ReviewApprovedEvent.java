package com.nexatech.product.event;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ReviewApprovedEvent {

    private UUID eventId;
    private String eventType;
    private Instant timestamp;
    private String reviewId;
    private String productId;
    private UUID userId;
    private int rating;
}
