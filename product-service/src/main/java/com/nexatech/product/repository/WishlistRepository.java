package com.nexatech.product.repository;

import com.nexatech.product.model.Wishlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WishlistRepository extends MongoRepository<Wishlist, String> {

    Optional<Wishlist> findByUserIdAndProductId(String userId, String productId);

    long deleteByUserIdAndProductId(String userId, String productId);

    Page<Wishlist> findByUserId(String userId, Pageable pageable);
}
