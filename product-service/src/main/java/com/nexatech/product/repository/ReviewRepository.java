package com.nexatech.product.repository;

import com.nexatech.product.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends MongoRepository<Review, String> {

    Optional<Review> findByProductIdAndUserId(String productId, UUID userId);

    Page<Review> findByProductIdAndApprovedTrue(String productId, Pageable pageable);

    Page<Review> findByProductId(String productId, Pageable pageable);

    List<Review> findByProductIdAndApprovedTrue(String productId);
}
