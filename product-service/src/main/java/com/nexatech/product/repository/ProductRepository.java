package com.nexatech.product.repository;

import com.nexatech.product.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySlug(String slug);

    boolean existsByCategoryIdsContainingAndActiveTrue(String categoryId);

    long countByCategoryIdsContainingAndActiveTrue(String categoryId);

    boolean existsByBrandIdAndActiveTrue(String brandId);
}
