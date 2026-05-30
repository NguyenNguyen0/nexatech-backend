package com.nexatech.product.controller;

import com.nexatech.product.command.review.DeleteReviewCommand;
import com.nexatech.product.command.review.MarkHelpfulCommand;
import com.nexatech.product.command.review.UpdateReviewCommand;
import com.nexatech.product.dto.request.ReviewRequest;
import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.PageResponse;
import com.nexatech.product.dto.response.ReviewResponse;
import com.nexatech.product.handler.review.ReviewCommandHandler;
import com.nexatech.product.handler.review.ReviewQueryHandler;
import com.nexatech.product.query.review.GetReviewsForAdminQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewManagementController {

    private final ReviewCommandHandler reviewCommandHandler;
    private final ReviewQueryHandler reviewQueryHandler;

    @GetMapping
    public ApiResponse<PageResponse<ReviewResponse>> getReviews(
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
            reviewQueryHandler.handle(new GetReviewsForAdminQuery(productId, rating, userId, from, to), pageable)));
    }

    @PutMapping("/{id}")
    public ApiResponse<ReviewResponse> updateReview(
            @PathVariable String id,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(reviewCommandHandler.handle(new UpdateReviewCommand(
            id,
            userId,
            role,
            request.getRating(),
            request.getTitle(),
            request.getContent(),
            request.getImages(),
            request.getVideos())));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteReview(
            @PathVariable String id,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        reviewCommandHandler.handle(new DeleteReviewCommand(id, userId, role));
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/helpful")
    public ApiResponse<ReviewResponse> markHelpful(@PathVariable String id) {
        return ApiResponse.ok(reviewCommandHandler.handle(new MarkHelpfulCommand(id)));
    }
}
