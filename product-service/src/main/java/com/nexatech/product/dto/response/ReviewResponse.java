package com.nexatech.product.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReviewResponse {

    private String id;
    private String productId;
    private UUID userId;
    private int rating;
    private String title;
    private String content;
    private List<String> images;
    private long helpfulCount;
    private Instant createdAt;
}
