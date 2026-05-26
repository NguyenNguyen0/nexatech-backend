package com.nexatech.product.query.review;

public record GetReviewsByProductSlugQuery(
        String slug,
        Integer rating) {}
