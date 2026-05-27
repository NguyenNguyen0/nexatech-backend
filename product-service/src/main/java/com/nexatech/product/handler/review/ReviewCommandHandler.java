package com.nexatech.product.handler.review;

import com.nexatech.product.command.review.CreateReviewCommand;
import com.nexatech.product.command.review.DeleteReviewCommand;
import com.nexatech.product.command.review.MarkHelpfulCommand;
import com.nexatech.product.command.review.UpdateReviewCommand;
import com.nexatech.product.dto.response.ReviewResponse;
import com.nexatech.product.exception.BadRequestException;
import com.nexatech.product.exception.ForbiddenException;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.ReviewMapper;
import com.nexatech.product.model.Product;
import com.nexatech.product.model.Review;
import com.nexatech.product.repository.ProductRepository;
import com.nexatech.product.repository.ReviewRepository;
import com.nexatech.product.service.ResilientDownstreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewCommandHandler {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ReviewMapper reviewMapper;
    private final ResilientDownstreamService resilientDownstreamService;

    @CacheEvict(value = "reviews", allEntries = true)
    public ReviewResponse handle(CreateReviewCommand command) {
        Product product = getActiveProductById(command.productId());

        reviewRepository.findByProductIdAndUserId(product.getId(), command.userId())
                .ifPresent(r -> {
                    throw new BadRequestException("You have already reviewed this product");
                });

        boolean purchased = Boolean.TRUE.equals(resilientDownstreamService.checkPurchased(command.userId(), product.getId()));
        if (!purchased) {
            throw new ForbiddenException("Only buyers can review this product");
        }

        Review review = Review.builder()
                .productId(product.getId())
                .userId(command.userId())
                .rating(command.rating())
                .title(command.title())
                .content(command.content())
                .images(command.images())
                .helpfulCount(0L)
                .build();

        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    @CacheEvict(value = "reviews", allEntries = true)
    public ReviewResponse handle(UpdateReviewCommand command) {
        Review review = findReview(command.id());
        assertOwnerOrAdmin(review, command.userId(), command.role());

        review.setRating(command.rating());
        review.setTitle(command.title());
        review.setContent(command.content());
        review.setImages(command.images());

        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    @CacheEvict(value = "reviews", allEntries = true)
    public void handle(DeleteReviewCommand command) {
        Review review = findReview(command.id());
        assertOwnerOrAdmin(review, command.userId(), command.role());
        reviewRepository.delete(review);
    }

    @CacheEvict(value = "reviews", allEntries = true)
    public ReviewResponse handle(MarkHelpfulCommand command) {
        Review review = findReview(command.id());
        review.setHelpfulCount(review.getHelpfulCount() + 1);
        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    private Product getActiveProductById(String productId) {
        return productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
    }

    private Review findReview(String reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));
    }

    private void assertOwnerOrAdmin(Review review, java.util.UUID userId, String role) {
        boolean isOwner = review.getUserId().equals(userId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("Only review owner or admin can modify this review");
        }
    }
}
