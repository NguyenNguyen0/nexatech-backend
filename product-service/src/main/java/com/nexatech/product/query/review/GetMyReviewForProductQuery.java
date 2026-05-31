package com.nexatech.product.query.review;

import java.util.UUID;

public record GetMyReviewForProductQuery(String productId, UUID userId) {}
