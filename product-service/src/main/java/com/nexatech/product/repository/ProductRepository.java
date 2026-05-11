package com.nexatech.product.repository;

import com.nexatech.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySlug(String slug);

    Optional<Product> findByIdAndActiveTrue(String id);

    Page<Product> findByCategoryIdsContaining(String categoryId, Pageable pageable);

    Page<Product> findByCategoryIdsContainingAndActiveTrue(String categoryId, Pageable pageable);

    Page<Product> findByBrandId(String brandId, Pageable pageable);

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndActiveTrue(String name, Pageable pageable);

    boolean existsByCategoryIdsContainingAndActiveTrue(String categoryId);

    long countByCategoryIdsContainingAndActiveTrue(String categoryId);

    boolean existsByBrandIdAndActiveTrue(String brandId);
}
