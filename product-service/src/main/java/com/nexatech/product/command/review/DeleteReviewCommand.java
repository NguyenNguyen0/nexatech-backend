package com.nexatech.product.command.review;

import java.util.UUID;

public record DeleteReviewCommand(
        String id,
        UUID userId,
        String role) {}
