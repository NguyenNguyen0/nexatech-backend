package com.nexatech.product.controller;

import com.nexatech.product.dto.request.ReviewRequest;
import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.PageResponse;
import com.nexatech.product.dto.response.ReviewResponse;
import com.nexatech.product.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> createReview(
            @PathVariable String productId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(reviewService.createReview(productId, userId, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<ReviewResponse>> getReviews(
            @PathVariable String productId,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(reviewService.getAllReviews(productId, pageable)));
    }
}
