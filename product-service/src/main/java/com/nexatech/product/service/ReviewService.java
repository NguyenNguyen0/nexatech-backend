package com.nexatech.product.service;

import com.nexatech.product.dto.request.ReviewRequest;
import com.nexatech.product.dto.response.ReviewResponse;
import com.nexatech.product.event.ProductEventProducer;
import com.nexatech.product.event.ReviewApprovedEvent;
import com.nexatech.product.exception.BadRequestException;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.ReviewMapper;
import com.nexatech.product.model.Review;
import com.nexatech.product.repository.ProductRepository;
import com.nexatech.product.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ReviewMapper reviewMapper;
    private final ProductEventProducer productEventProducer;

    public ReviewResponse createReview(String productId, UUID userId, ReviewRequest request) {
        productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        reviewRepository.findByProductIdAndUserId(productId, userId)
                .ifPresent(r -> {
                    throw new BadRequestException("You have already reviewed this product");
                });

        Review review = reviewMapper.toEntity(request);
        review.setProductId(productId);
        review.setUserId(userId);
        review.setApproved(false);

        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    public Page<ReviewResponse> getApprovedReviews(String productId, Pageable pageable) {
        return reviewRepository.findByProductIdAndApprovedTrue(productId, pageable)
                .map(reviewMapper::toResponse);
    }

    public Page<ReviewResponse> getAllReviews(String productId, Pageable pageable) {
        return reviewRepository.findByProductId(productId, pageable)
                .map(reviewMapper::toResponse);
    }

    public ReviewResponse approveReview(String reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));

        review.setApproved(true);
        Review saved = reviewRepository.save(review);

        publishReviewApprovedEvent(saved);

        return reviewMapper.toResponse(saved);
    }

    public void deleteReview(String reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));
        reviewRepository.delete(review);
    }

    private void publishReviewApprovedEvent(Review review) {
        ReviewApprovedEvent event = ReviewApprovedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("REVIEW_APPROVED")
                .timestamp(Instant.now())
                .reviewId(review.getId())
                .productId(review.getProductId())
                .userId(review.getUserId())
                .rating(review.getRating())
                .build();
        productEventProducer.publishReviewEvent(review.getProductId(), event);
    }
}
