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
import java.util.regex.Pattern;

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
        if (existsByNameInParent(normalizedName, command.parentId(), null)) {
            throw new BadRequestException("Category name already exists: " + normalizedName);
        }

        if (StringUtils.hasText(command.parentId())) {
            getActiveCategory(command.parentId());
        }

        Category category = Category.builder()
                .name(normalizedName)
                .slug(generateUniqueSlug(preferredSlug(command.metadata(), normalizedName), null))
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

        if (existsByNameInParent(normalizedName, command.parentId(), command.id())) {
            throw new BadRequestException("Category name already exists: " + normalizedName);
        }

        if (StringUtils.hasText(command.parentId())) {
            if (MongoIdUtils.equalsId(command.id(), command.parentId())) {
                throw new BadRequestException("Category cannot be its own parent");
            }
            getActiveCategory(command.parentId());
        }

        String oldName = category.getName();
        String oldSlug = category.getSlug();
        category.setName(normalizedName);
        category.setDescription(command.description());
        category.setParentId(command.parentId());
        category.setSortOrder(command.sortOrder());
        category.setFilter(command.filter());
        category.setMetadata(sanitizeMetadata(command.metadata()));
        String preferredSlug = preferredSlug(command.metadata(), normalizedName);
        if (!normalizedName.equalsIgnoreCase(oldName) || !preferredSlug.equals(oldSlug)) {
            category.setSlug(generateUniqueSlug(preferredSlug, command.id()));
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

    private boolean existsByNameInParent(String name, String parentId, String excludedId) {
        Criteria criteria = Criteria.where("name").regex("^" + Pattern.quote(name) + "$", "i");
        criteria = StringUtils.hasText(parentId)
                ? criteria.and("parentId").is(parentId)
                : criteria.and("parentId").in(null, "");
        if (StringUtils.hasText(excludedId)) {
            criteria = criteria.and("_id").ne(excludedId);
        }
        return mongoTemplate.exists(Query.query(criteria), Category.class);
    }

    private String generateUniqueSlug(String value, String currentCategoryId) {
        String base = toSlug(value);
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

    private String preferredSlug(Map<String, Object> metadata, String fallback) {
        if (metadata == null) {
            return fallback;
        }
        Object expectedSlug = metadata.get("expectedSlug");
        return expectedSlug instanceof String slug && StringUtils.hasText(slug) ? slug : fallback;
    }
}
