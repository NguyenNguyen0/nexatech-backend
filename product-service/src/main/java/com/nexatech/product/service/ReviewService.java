package com.nexatech.product.service;

import com.nexatech.product.dto.request.ReviewRequest;
import com.nexatech.product.dto.response.ReviewResponse;
import com.nexatech.product.client.OrderClient;
import com.nexatech.product.exception.BadRequestException;
import com.nexatech.product.exception.ForbiddenException;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.ReviewMapper;
import com.nexatech.product.model.Review;
import com.nexatech.product.repository.ProductRepository;
import com.nexatech.product.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ReviewMapper reviewMapper;
    private final OrderClient orderClient;

    public ReviewResponse createReview(String productId, UUID userId, ReviewRequest request) {
        productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        reviewRepository.findByProductIdAndUserId(productId, userId)
                .ifPresent(r -> {
                    throw new BadRequestException("You have already reviewed this product");
                });

        boolean purchased = Boolean.TRUE.equals(orderClient.checkPurchased(userId, productId).getData());
        if (!purchased) {
            throw new ForbiddenException("Only buyers can review this product");
        }

        Review review = reviewMapper.toEntity(request);
        review.setProductId(productId);
        review.setUserId(userId);
        review.setHelpfulCount(0L);

        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    public Page<ReviewResponse> getAllReviews(String productId, Pageable pageable) {
        productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        return reviewRepository.findByProductId(productId, pageable)
                .map(reviewMapper::toResponse);
    }

    public ReviewResponse updateReview(String reviewId, UUID userId, String role, ReviewRequest request) {
        Review review = findReview(reviewId);
        assertOwnerOrAdmin(review, userId, role);

        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setContent(request.getContent());
        review.setImages(request.getImages());

        Review saved = reviewRepository.save(review);
        return reviewMapper.toResponse(saved);
    }

    public void deleteReview(String reviewId, UUID userId, String role) {
        Review review = findReview(reviewId);
        assertOwnerOrAdmin(review, userId, role);
        reviewRepository.delete(review);
    }

    private Review findReview(String reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));
        return review;
    }

    private void assertOwnerOrAdmin(Review review, UUID userId, String role) {
        boolean isOwner = review.getUserId().equals(userId);
        boolean isAdmin = role != null && "ADMIN".equalsIgnoreCase(role);
        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("Only review owner or admin can modify this review");
        }
    }
}
