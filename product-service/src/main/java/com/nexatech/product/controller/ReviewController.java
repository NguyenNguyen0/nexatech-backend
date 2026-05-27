package com.nexatech.product.controller;

import com.nexatech.product.command.review.CreateReviewCommand;
import com.nexatech.product.dto.request.ReviewRequest;
import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.PageResponse;
import com.nexatech.product.dto.response.ReviewResponse;
import com.nexatech.product.handler.review.ReviewCommandHandler;
import com.nexatech.product.handler.review.ReviewQueryHandler;
import com.nexatech.product.query.review.GetReviewsByProductIdQuery;
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

    private final ReviewCommandHandler reviewCommandHandler;
    private final ReviewQueryHandler reviewQueryHandler;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> createReview(
            @PathVariable String productId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(reviewCommandHandler.handle(new CreateReviewCommand(
            productId,
            userId,
            request.getRating(),
            request.getTitle(),
            request.getContent(),
            request.getImages())));
    }

    @GetMapping
    public ApiResponse<PageResponse<ReviewResponse>> getReviews(
            @PathVariable String productId,
            @RequestParam(required = false) Integer rating,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
                reviewQueryHandler.handle(new GetReviewsByProductIdQuery(productId, rating), pageable)));
    }
}
