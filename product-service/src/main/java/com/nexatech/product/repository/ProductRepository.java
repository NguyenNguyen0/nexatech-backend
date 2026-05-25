package com.nexatech.product.repository;

import com.nexatech.product.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySlug(String slug);

    List<Product> findByIdInAndActiveTrue(List<String> ids);

    boolean existsByCategoryIdsContainingAndActiveTrue(String categoryId);

    long countByCategoryIdsContainingAndActiveTrue(String categoryId);

    boolean existsByBrandIdAndActiveTrue(String brandId);
}
