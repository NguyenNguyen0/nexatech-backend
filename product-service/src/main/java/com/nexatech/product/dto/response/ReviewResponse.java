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
    private String productName;
    private String productSlug;
    private String productThumbnail;
    private UUID userId;
    private String userName;
    private String avatar;
    private int rating;
    private String title;
    private String content;
    private List<String> images;
    private List<String> videos;
    private long helpfulCount;
    private Instant createdAt;
}
