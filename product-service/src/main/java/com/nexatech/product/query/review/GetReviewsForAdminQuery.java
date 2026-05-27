package com.nexatech.product.query.review;

import java.time.Instant;
import java.util.UUID;

public record GetReviewsForAdminQuery(
        String productId,
        Integer rating,
        UUID userId,
        Instant from,
        Instant to) {}
