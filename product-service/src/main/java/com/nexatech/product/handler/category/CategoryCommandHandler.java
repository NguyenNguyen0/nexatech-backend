package com.nexatech.product.handler.category;

import com.nexatech.product.command.category.CreateCategoryCommand;
import com.nexatech.product.command.category.DeleteCategoryCommand;
import com.nexatech.product.command.category.RestoreCategoryCommand;
import com.nexatech.product.command.category.UpdateCategoryCommand;
import com.nexatech.product.dto.response.CategoryResponse;
import com.nexatech.product.exception.BadRequestException;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.CategoryMapper;
import com.nexatech.product.model.Category;
import com.nexatech.product.repository.CategoryRepository;
import com.nexatech.product.repository.ProductRepository;
import com.nexatech.product.util.MongoIdUtils;
import com.nexatech.product.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CategoryCommandHandler {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;
    private final MongoTemplate mongoTemplate;

    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse handle(CreateCategoryCommand command) {
        String normalizedName = TextUtils.normalizeName(command.name());
        if (categoryRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new BadRequestException("Category name already exists: " + normalizedName);
        }

        if (StringUtils.hasText(command.parentId())) {
            getActiveCategory(command.parentId());
        }

        Category category = Category.builder()
                .name(normalizedName)
                .slug(generateUniqueSlug(normalizedName, null))
                .description(command.description())
                .parentId(command.parentId())
                .sortOrder(command.sortOrder())
                .active(true)
                .filter(command.filter())
                .metadata(sanitizeMetadata(command.metadata()))
                .build();

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse handle(UpdateCategoryCommand command) {
        Category category = getActiveCategory(command.id());
        String normalizedName = TextUtils.normalizeName(command.name());

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, command.id())) {
            throw new BadRequestException("Category name already exists: " + normalizedName);
        }

        if (StringUtils.hasText(command.parentId())) {
            if (MongoIdUtils.equalsId(command.id(), command.parentId())) {
                throw new BadRequestException("Category cannot be its own parent");
            }
            getActiveCategory(command.parentId());
        }

        String oldName = category.getName();
        category.setName(normalizedName);
        category.setDescription(command.description());
        category.setParentId(command.parentId());
        category.setSortOrder(command.sortOrder());
        category.setFilter(command.filter());
        category.setMetadata(sanitizeMetadata(command.metadata()));
        if (!normalizedName.equalsIgnoreCase(oldName)) {
            category.setSlug(generateUniqueSlug(normalizedName, command.id()));
        }

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @CacheEvict(value = "categories", allEntries = true)
    public void handle(DeleteCategoryCommand command) {
        Category category = getActiveCategory(command.id());

        if (categoryRepository.existsByParentIdAndActiveTrue(command.id())) {
            throw new BadRequestException("Cannot delete category with active subcategories");
        }

        if (productRepository.existsByCategoryIdsContainingAndActiveTrue(command.id())) {
            throw new BadRequestException("Cannot delete category with active products");
        }

        category.setActive(false);
        categoryRepository.save(category);
    }

    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse handle(RestoreCategoryCommand command) {
        Category category = getCategoryById(command.id());
        if (category.isActive()) {
            return categoryMapper.toResponse(category);
        }

        if (StringUtils.hasText(category.getParentId())) {
            getActiveCategory(category.getParentId());
        }

        category.setActive(true);
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    private Category getActiveCategory(String id) {
        Query query = Query.query(new Criteria().andOperator(
                MongoIdUtils.idEqualsCriteria("_id", id),
                Criteria.where("active").is(true)
        ));
        Category category = mongoTemplate.findOne(query, Category.class);
        if (category == null) {
            throw new ResourceNotFoundException("Category", "id", id);
        }
        return category;
    }

    private Category getCategoryById(String id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(metadata);
    }

    private String generateUniqueSlug(String name, String currentCategoryId) {
        String base = toSlug(name);
        String candidate = base;
        int suffix = 1;

        while (true) {
            Category existing = categoryRepository.findBySlug(candidate).orElse(null);
            if (existing == null || MongoIdUtils.equalsId(currentCategoryId, existing.getId())) {
                return candidate;
            }
            candidate = base + "-" + suffix++;
        }
    }

    private String toSlug(String value) {
        return TextUtils.toSlug(value, "category");
    }
}
