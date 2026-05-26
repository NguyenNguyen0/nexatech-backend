package com.nexatech.product.command.review;

import java.util.List;
import java.util.UUID;

public record CreateReviewCommand(
        String productId,
        UUID userId,
        Integer rating,
        String title,
        String content,
        List<String> images) {}
