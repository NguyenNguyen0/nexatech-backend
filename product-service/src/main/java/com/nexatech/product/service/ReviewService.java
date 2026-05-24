package com.nexatech.product.service;

import com.nexatech.product.client.OrderClient;
import com.nexatech.product.dto.request.ReviewRequest;
import com.nexatech.product.dto.response.ReviewResponse;
import com.nexatech.product.exception.BadRequestException;
import com.nexatech.product.exception.ForbiddenException;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.ReviewMapper;
import com.nexatech.product.model.Product;
import com.nexatech.product.model.Review;
import com.nexatech.product.repository.ProductRepository;
import com.nexatech.product.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nexatech.product.util.ProductQueryUtils.sanitizePageable;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of("createdAt", "rating", "helpfulCount");

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ReviewMapper reviewMapper;
    private final OrderClient orderClient;
    private final MongoTemplate mongoTemplate;

    public ReviewResponse createReview(String productId, UUID userId, ReviewRequest request) {
        productRepository.findById(productId)
                .filter(Product::isActive)
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

    public Page<ReviewResponse> getAllReviews(String productId, Integer rating, Pageable pageable) {
        productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        if (rating != null) {
            return reviewRepository.findByProductIdAndRating(productId, rating, pageable)
                    .map(this::toReviewResponse);
        }
        return reviewRepository.findByProductId(productId, pageable)
                .map(this::toReviewResponse);
    }

    public Page<ReviewResponse> getReviewsForAdmin(String productId,
                                                   Integer rating,
                                                   UUID userId,
                                                   Instant from,
                                                   Instant to,
                                                   Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("Invalid date range: from must be <= to");
        }

        Pageable safePageable = sanitizePageable(pageable, Sort.by(Sort.Direction.DESC, "createdAt"), ALLOWED_SORT_FIELDS);

        List<Criteria> criteriaList = new ArrayList<>();
        if (StringUtils.hasText(productId)) {
            criteriaList.add(Criteria.where("productId").is(productId));
        }
        if (rating != null) {
            criteriaList.add(Criteria.where("rating").is(rating));
        }
        if (userId != null) {
            criteriaList.add(Criteria.where("userId").is(userId));
        }
        if (from != null || to != null) {
            Criteria createdAtCriteria = Criteria.where("createdAt");
            if (from != null) {
                createdAtCriteria = createdAtCriteria.gte(from);
            }
            if (to != null) {
                createdAtCriteria = createdAtCriteria.lte(to);
            }
            criteriaList.add(createdAtCriteria);
        }

        Query query = criteriaList.isEmpty()
                ? new Query()
                : Query.query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        query.with(safePageable);

        List<ReviewResponse> content = mongoTemplate.find(query, Review.class).stream()
                .map(this::toReviewResponse)
                .toList();
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Review.class);
        return new PageImpl<>(content, safePageable, total);
    }

    public ReviewResponse updateReview(String reviewId, UUID userId, String role, ReviewRequest request) {
        Review review = findReview(reviewId);
        assertOwnerOrAdmin(review, userId, role);

        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setContent(request.getContent());
        review.setImages(request.getImages());

        Review saved = reviewRepository.save(review);
        return toReviewResponse(saved);
    }

    public void deleteReview(String reviewId, UUID userId, String role) {
        Review review = findReview(reviewId);
        assertOwnerOrAdmin(review, userId, role);
        reviewRepository.delete(review);
    }

    public ReviewResponse markHelpful(String reviewId) {
        Review review = findReview(reviewId);
        review.setHelpfulCount(review.getHelpfulCount() + 1);
        return toReviewResponse(reviewRepository.save(review));
    }

    private ReviewResponse toReviewResponse(Review review) {
        ReviewResponse response = reviewMapper.toResponse(review);
        productRepository.findById(review.getProductId()).ifPresent(product -> {
            response.setProductName(product.getName());
            response.setProductSlug(product.getSlug());
            response.setProductThumbnail(product.getThumbnail());
        });
        return response;
    }

    private Review findReview(String reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));
        return review;
    }

    private void assertOwnerOrAdmin(Review review, UUID userId, String role) {
        boolean isOwner = review.getUserId().equals(userId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("Only review owner or admin can modify this review");
        }
    }
}
