package com.nexatech.product.repository;

import com.nexatech.product.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends MongoRepository<Review, String> {

    Optional<Review> findByProductIdAndUserId(String productId, UUID userId);

    Page<Review> findByProductIdAndApprovedTrue(String productId, Pageable pageable);

    Page<Review> findByProductId(String productId, Pageable pageable);

    @Aggregation(pipeline = {
            "{ $match: { productId: ?0, approved: true } }",
            "{ $group: { _id: null, avg: { $avg: '$rating' } } }"
    })
    Double getAverageRatingByProductId(String productId);
}
