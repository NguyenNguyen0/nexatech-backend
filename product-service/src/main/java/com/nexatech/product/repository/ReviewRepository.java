package com.nexatech.product.repository;

import com.nexatech.product.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends MongoRepository<Review, String> {

    Optional<Review> findByProductIdAndUserId(String productId, UUID userId);

    List<Review> findByProductIdAndUserIdOrderByCreatedAtDesc(String productId, UUID userId);

    long countByProductIdAndUserId(String productId, UUID userId);

    Page<Review> findByProductId(String productId, Pageable pageable);

    Page<Review> findByProductIdAndRating(String productId, int rating, Pageable pageable);

    @Aggregation(pipeline = {
            "{ $match: { productId: ?0 } }",
            "{ $group: { _id: null, avg: { $avg: '$rating' } } }"
    })
    Double getAverageRatingByProductId(String productId);

    long countByProductId(String productId);
}
