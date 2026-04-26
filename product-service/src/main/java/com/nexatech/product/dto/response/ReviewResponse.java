package com.nexatech.product.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ReviewResponse {

    private String id;
    private String productId;
    private UUID userId;
    private int rating;
    private String comment;
    private boolean approved;
    private Instant createdAt;
}
