package com.nexatech.product.controller;

import com.nexatech.product.command.review.CreateReviewCommand;
import com.nexatech.product.dto.request.ReviewRequest;
import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.PageResponse;
import com.nexatech.product.dto.response.ReviewMediaUploadResponse;
import com.nexatech.product.dto.response.ReviewResponse;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.handler.review.ReviewCommandHandler;
import com.nexatech.product.handler.review.ReviewQueryHandler;
import com.nexatech.product.model.Product;
import com.nexatech.product.query.review.GetMyReviewForProductQuery;
import com.nexatech.product.query.review.GetReviewsByProductIdQuery;
import com.nexatech.product.repository.ProductRepository;
import com.nexatech.product.service.ReviewMediaStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewCommandHandler reviewCommandHandler;
    private final ReviewQueryHandler reviewQueryHandler;
    private final ReviewMediaStorageService reviewMediaStorageService;
    private final ProductRepository productRepository;

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
            request.getImages(),
            request.getVideos())));
    }

    @GetMapping("/mine")
    public ApiResponse<ReviewResponse> getMyReview(
            @PathVariable String productId,
            @RequestHeader("X-User-Id") UUID userId) {
        return ApiResponse.ok(reviewQueryHandler.handle(new GetMyReviewForProductQuery(productId, userId)));
    }

    @GetMapping
    public ApiResponse<PageResponse<ReviewResponse>> getReviews(
            @PathVariable String productId,
            @RequestParam(required = false) Integer rating,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
                reviewQueryHandler.handle(new GetReviewsByProductIdQuery(productId, rating), pageable)));
    }

    @PostMapping("/media")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewMediaUploadResponse> uploadReviewMedia(
            @PathVariable String productId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestPart("file") MultipartFile file) {
        assertActiveProduct(productId);
        return ApiResponse.ok(reviewMediaStorageService.upload(file));
    }

    private void assertActiveProduct(String productId) {
        productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
    }
}
