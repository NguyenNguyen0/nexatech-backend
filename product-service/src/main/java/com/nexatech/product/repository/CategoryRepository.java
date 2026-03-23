package com.nexatech.product.repository;

import com.nexatech.product.model.Category;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {

    Optional<Category> findBySlug(String slug);

    Optional<Category> findByIdAndActiveTrue(String id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, String id);

    List<Category> findByParentId(String parentId);

    List<Category> findByParentIdAndActiveTrue(String parentId);

    boolean existsByParentIdAndActiveTrue(String parentId);

    List<Category> findByActiveTrue();
}
