package com.nexatech.product.controller;

import com.nexatech.product.dto.request.ReviewRequest;
import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.ReviewResponse;
import com.nexatech.product.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewManagementController {

    private final ReviewService reviewService;

    @PutMapping("/{id}")
    public ApiResponse<ReviewResponse> updateReview(
            @PathVariable String id,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(reviewService.updateReview(id, userId, role, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteReview(
            @PathVariable String id,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        reviewService.deleteReview(id, userId, role);
        return ApiResponse.ok(null);
    }
}
