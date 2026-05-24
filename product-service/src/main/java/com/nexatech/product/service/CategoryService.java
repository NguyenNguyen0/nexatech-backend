package com.nexatech.product.service;

import com.nexatech.product.dto.request.CategoryRequest;
import com.nexatech.product.dto.response.CategoryMenuItemResponse;
import com.nexatech.product.dto.response.CategoryMetaResponse;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final String ROOT_PARENT_KEY = "__root__";

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;
    private final MongoTemplate mongoTemplate;

    public CategoryResponse createCategory(CategoryRequest request) {
        String normalizedName = TextUtils.normalizeName(request.getName());
        if (categoryRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new BadRequestException("Category name already exists: " + normalizedName);
        }

        if (StringUtils.hasText(request.getParentId())) {
            getActiveCategory(request.getParentId());
        }

        Category category = categoryMapper.toEntity(request);
        category.setName(normalizedName);
        category.setSlug(generateUniqueSlug(normalizedName, null));
        category.setActive(true);
        category.setMetadata(sanitizeMetadata(request.getMetadata()));

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    public CategoryResponse updateCategory(String id, CategoryRequest request) {
        Category category = getActiveCategory(id);
        String normalizedName = TextUtils.normalizeName(request.getName());

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw new BadRequestException("Category name already exists: " + normalizedName);
        }

        if (StringUtils.hasText(request.getParentId())) {
            if (MongoIdUtils.equalsId(id, request.getParentId())) {
                throw new BadRequestException("Category cannot be its own parent");
            }
            getActiveCategory(request.getParentId());
        }

        String oldName = category.getName();
        categoryMapper.updateEntity(request, category);
        category.setName(normalizedName);
        category.setMetadata(sanitizeMetadata(request.getMetadata()));
        if (!normalizedName.equalsIgnoreCase(oldName)) {
            category.setSlug(generateUniqueSlug(normalizedName, id));
        }

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    public CategoryResponse getCategory(String id) {
        return categoryMapper.toResponse(getActiveCategory(id));
    }

    public CategoryResponse getCategoryBySlug(String slug) {
        return categoryMapper.toResponse(getActiveCategoryBySlug(slug));
    }

    public List<CategoryResponse> getAllCategories(Boolean active, boolean includeDeleted) {
        Query query = new Query().with(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("name")));
        if (active != null) {
            query.addCriteria(Criteria.where("active").is(active));
        } else if (!includeDeleted) {
            query.addCriteria(Criteria.where("active").is(true));
        }

        return mongoTemplate.find(query, Category.class).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    public List<CategoryMenuItemResponse> getSubCategories(String parentId) {
        getActiveCategory(parentId);
        List<Category> activeCategories = categoryRepository.findByActiveTrue();
        Map<String, List<Category>> childrenByParent = groupChildrenByParent(activeCategories);
        return buildCategoryTree(parentId, childrenByParent);
    }

    public List<CategoryMenuItemResponse> getSubCategoriesBySlug(String slug) {
        Category parentCategory = getActiveCategoryBySlug(slug);
        return getSubCategories(parentCategory.getId());
    }

    public List<CategoryMenuItemResponse> getCategoryMenu() {
        List<Category> activeCategories = categoryRepository.findByActiveTrue();
        Map<String, List<Category>> childrenByParent = groupChildrenByParent(activeCategories);
        return buildCategoryTree(ROOT_PARENT_KEY, childrenByParent);
    }

    public CategoryMetaResponse getCategoryMetaBySlug(String slug) {
        Category category = getActiveCategoryBySlug(slug);
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

    public void deleteCategory(String id) {
        Category category = getActiveCategory(id);

        if (categoryRepository.existsByParentIdAndActiveTrue(id)) {
            throw new BadRequestException("Cannot delete category with active subcategories");
        }

        if (productRepository.existsByCategoryIdsContainingAndActiveTrue(id)) {
            throw new BadRequestException("Cannot delete category with active products");
        }

        category.setActive(false);
        categoryRepository.save(category);
    }

    public CategoryResponse restoreCategory(String id) {
        Category category = getCategoryById(id);
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
