package com.nexatech.product.query.review;

public record GetReviewsByProductIdQuery(
        String productId,
        Integer rating) {}
