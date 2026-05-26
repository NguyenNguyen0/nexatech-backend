package com.nexatech.product.command.review;

import java.util.List;
import java.util.UUID;

public record UpdateReviewCommand(
        String id,
        UUID userId,
        String role,
        Integer rating,
        String title,
        String content,
        List<String> images) {}
