package com.nexatech.product.repository;

import com.nexatech.product.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySlug(String slug);

    Page<Product> findByActiveTrue(Pageable pageable);

    List<Product> findByIdInAndActiveTrue(List<String> ids);

    boolean existsByCategoryIdsContainingAndActiveTrue(String categoryId);

    long countByCategoryIdsContainingAndActiveTrue(String categoryId);

    boolean existsByBrandIdAndActiveTrue(String brandId);
}
