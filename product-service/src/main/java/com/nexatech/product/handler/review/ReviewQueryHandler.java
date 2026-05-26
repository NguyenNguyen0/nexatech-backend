package com.nexatech.product.handler.review;

import com.nexatech.product.dto.response.ReviewResponse;
import com.nexatech.product.exception.BadRequestException;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.ReviewMapper;
import com.nexatech.product.model.Product;
import com.nexatech.product.model.Review;
import com.nexatech.product.query.review.GetReviewsByProductIdQuery;
import com.nexatech.product.query.review.GetReviewsByProductSlugQuery;
import com.nexatech.product.query.review.GetReviewsForAdminQuery;
import com.nexatech.product.repository.ProductRepository;
import com.nexatech.product.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.nexatech.product.util.ProductQueryUtils.sanitizePageable;

@Component
@RequiredArgsConstructor
public class ReviewQueryHandler {

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of("createdAt", "rating", "helpfulCount");

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ReviewMapper reviewMapper;
    private final MongoTemplate mongoTemplate;

    @Cacheable(value = "reviews", key = "#query.productId() + ':' + (#query.rating() == null ? 'all' : #query.rating()) + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<ReviewResponse> handle(GetReviewsByProductIdQuery query, Pageable pageable) {
        Product product = getActiveProductById(query.productId());
        if (query.rating() != null) {
            return reviewRepository.findByProductIdAndRating(product.getId(), query.rating(), pageable)
                    .map(this::toReviewResponse);
        }
        return reviewRepository.findByProductId(product.getId(), pageable)
                .map(this::toReviewResponse);
    }

    public Page<ReviewResponse> handle(GetReviewsByProductSlugQuery query, Pageable pageable) {
        Product product = productRepository.findBySlug(query.slug())
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", query.slug()));
        return handle(new GetReviewsByProductIdQuery(product.getId(), query.rating()), pageable);
    }

    public Page<ReviewResponse> handle(GetReviewsForAdminQuery query, Pageable pageable) {
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new BadRequestException("Invalid date range: from must be <= to");
        }

        Pageable safePageable = sanitizePageable(pageable, Sort.by(Sort.Direction.DESC, "createdAt"), ALLOWED_SORT_FIELDS);

        List<Criteria> criteriaList = new ArrayList<>();
        if (StringUtils.hasText(query.productId())) {
            criteriaList.add(Criteria.where("productId").is(query.productId()));
        }
        if (query.rating() != null) {
            criteriaList.add(Criteria.where("rating").is(query.rating()));
        }
        if (query.userId() != null) {
            criteriaList.add(Criteria.where("userId").is(query.userId()));
        }
        if (query.from() != null || query.to() != null) {
            Criteria createdAtCriteria = Criteria.where("createdAt");
            if (query.from() != null) {
                createdAtCriteria = createdAtCriteria.gte(query.from());
            }
            if (query.to() != null) {
                createdAtCriteria = createdAtCriteria.lte(query.to());
            }
            criteriaList.add(createdAtCriteria);
        }

        Query mongoQuery = criteriaList.isEmpty()
                ? new Query()
                : Query.query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        mongoQuery.with(safePageable);

        List<ReviewResponse> content = mongoTemplate.find(mongoQuery, Review.class).stream()
                .map(this::toReviewResponse)
                .toList();
        long total = mongoTemplate.count(Query.of(mongoQuery).limit(-1).skip(-1), Review.class);
        return new PageImpl<>(content, safePageable, total);
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

    private Product getActiveProductById(String productId) {
        return productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
    }
}
