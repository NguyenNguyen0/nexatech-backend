package com.nexatech.product.handler.category;

import com.nexatech.product.dto.response.CategoryMenuItemResponse;
import com.nexatech.product.dto.response.CategoryMetaResponse;
import com.nexatech.product.dto.response.CategoryResponse;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.CategoryMapper;
import com.nexatech.product.model.Category;
import com.nexatech.product.query.category.*;
import com.nexatech.product.repository.CategoryRepository;
import com.nexatech.product.repository.ProductRepository;
import com.nexatech.product.util.MongoIdUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

@Component
@RequiredArgsConstructor
public class CategoryQueryHandler {

    private static final String ROOT_PARENT_KEY = "__root__";

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;
    private final MongoTemplate mongoTemplate;

    public CategoryResponse handle(GetCategoryByIdQuery query) {
        return categoryMapper.toResponse(getActiveCategory(query.id()));
    }

    @Cacheable(value = "categories", key = "'slug:' + #query.slug()")
    public CategoryResponse handle(GetCategoryBySlugQuery query) {
        return categoryMapper.toResponse(getActiveCategoryBySlug(query.slug()));
    }

    public List<CategoryResponse> handle(GetAllCategoriesQuery query) {
        Query mongoQuery = new Query().with(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("name")));
        if (query.active() != null) {
            mongoQuery.addCriteria(Criteria.where("active").is(query.active()));
        } else if (!query.includeDeleted()) {
            mongoQuery.addCriteria(Criteria.where("active").is(true));
        }

        return mongoTemplate.find(mongoQuery, Category.class).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    public List<CategoryMenuItemResponse> handle(GetSubCategoriesQuery query) {
        getActiveCategory(query.parentId());
        List<Category> activeCategories = categoryRepository.findByActiveTrue();
        Map<String, List<Category>> childrenByParent = groupChildrenByParent(activeCategories);
        return buildCategoryTree(query.parentId(), childrenByParent);
    }

    public List<CategoryMenuItemResponse> handle(GetSubCategoriesBySlugQuery query) {
        Category parentCategory = getActiveCategoryBySlug(query.slug());
        return handle(new GetSubCategoriesQuery(parentCategory.getId()));
    }

    @Cacheable(value = "categories", key = "'menu'")
    public List<CategoryMenuItemResponse> handle(GetCategoryMenuQuery query) {
        List<Category> activeCategories = categoryRepository.findByActiveTrue();
        Map<String, List<Category>> childrenByParent = groupChildrenByParent(activeCategories);
        return buildCategoryTree(ROOT_PARENT_KEY, childrenByParent);
    }

    @Cacheable(value = "categories", key = "'meta:slug:' + #query.slug()")
    public CategoryMetaResponse handle(GetCategoryMetaBySlugQuery query) {
        Category category = getActiveCategoryBySlug(query.slug());
        Map<String, Object> metadata = sanitizeMetadata(category.getMetadata());
        List<String> filterableSpecs = category.getFilter() == null || category.getFilter().getFilterableSpecs() == null
                ? List.of()
                : List.copyOf(category.getFilter().getFilterableSpecs());

        return CategoryMetaResponse.builder()
                .id(category.getId())
                .slug(category.getSlug())
                .name(category.getName())
                .title(resolveMetadataString(metadata, "seoTitle", category.getName()))
                .description(resolveMetadataString(metadata, "seoDescription", category.getDescription()))
                .bannerImage(resolveMetadataString(metadata, "bannerImage", null))
                .productCount(productRepository.countByCategoryIdsContainingAndActiveTrue(category.getId()))
                .subcategoryCount(categoryRepository.findByParentIdAndActiveTrue(category.getId()).size())
                .filterableSpecs(filterableSpecs)
                .metadata(metadata)
                .build();
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

    private Category getActiveCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .filter(Category::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", slug));
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(metadata);
    }

    private String resolveMetadataString(Map<String, Object> metadata,
                                         String key,
                                         String fallback) {
        Object value = metadata.get(key);
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            return stringValue;
        }
        return fallback;
    }

    private Map<String, List<Category>> groupChildrenByParent(List<Category> categories) {
        Map<String, List<Category>> grouped = new LinkedHashMap<>();
        for (Category category : categories) {
            String parentKey = StringUtils.hasText(category.getParentId())
                    ? category.getParentId()
                    : ROOT_PARENT_KEY;
            grouped.computeIfAbsent(parentKey, ignored -> new ArrayList<>()).add(category);
        }

        grouped.values().forEach(list -> list.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder())));
        return grouped;
    }

    private List<CategoryMenuItemResponse> buildCategoryTree(String parentId,
                                                             Map<String, List<Category>> childrenByParent) {
        List<Category> directChildren = childrenByParent.getOrDefault(parentId, Collections.emptyList());
        return directChildren.stream()
                .map(category -> toMenuItem(category, childrenByParent))
                .toList();
    }

    private CategoryMenuItemResponse toMenuItem(Category category,
                                                Map<String, List<Category>> childrenByParent) {
        return CategoryMenuItemResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .sortOrder(category.getSortOrder())
                .metadata(sanitizeMetadata(category.getMetadata()))
                .children(buildCategoryTree(category.getId(), childrenByParent))
                .build();
    }
}
