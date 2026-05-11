package com.nexatech.product.service;

import com.nexatech.product.dto.request.CategoryRequest;
import com.nexatech.product.dto.response.CategoryResponse;
import com.nexatech.product.exception.BadRequestException;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.CategoryMapper;
import com.nexatech.product.model.Category;
import com.nexatech.product.repository.CategoryRepository;
import com.nexatech.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    public CategoryResponse createCategory(CategoryRequest request) {
        String normalizedName = normalizeName(request.getName());
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

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    public CategoryResponse updateCategory(String id, CategoryRequest request) {
        Category category = getActiveCategory(id);
        String normalizedName = normalizeName(request.getName());

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw new BadRequestException("Category name already exists: " + normalizedName);
        }

        if (StringUtils.hasText(request.getParentId())) {
            if (id.equals(request.getParentId())) {
                throw new BadRequestException("Category cannot be its own parent");
            }
            getActiveCategory(request.getParentId());
        }

        String oldName = category.getName();
        categoryMapper.updateEntity(request, category);
        category.setName(normalizedName);
        if (!normalizedName.equalsIgnoreCase(oldName)) {
            category.setSlug(generateUniqueSlug(normalizedName, id));
        }

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    public CategoryResponse getCategory(String id) {
        return categoryMapper.toResponse(getActiveCategory(id));
    }

    public CategoryResponse getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .filter(Category::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", slug));
        return categoryMapper.toResponse(category);
    }

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findByActiveTrue().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(categoryMapper::toResponse)
                .toList();
    }

    public List<CategoryResponse> getSubCategories(String parentId) {
        getActiveCategory(parentId);
        return categoryRepository.findByParentIdAndActiveTrue(parentId).stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(categoryMapper::toResponse)
                .toList();
    }

    public void deleteCategory(String id) {
        Category category = getActiveCategory(id);

        if (categoryRepository.existsByParentIdAndActiveTrue(id)) {
            throw new BadRequestException("Cannot delete category with active subcategories");
        }

        if (productRepository.existsByCategoryIdAndActiveTrue(id)) {
            throw new BadRequestException("Cannot delete category with active products");
        }

        category.setActive(false);
        categoryRepository.save(category);
    }

    private Category getActiveCategory(String id) {
        return categoryRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    private String generateUniqueSlug(String name, String currentCategoryId) {
        String base = toSlug(name);
        String candidate = base;
        int suffix = 1;

        while (true) {
            Category existing = categoryRepository.findBySlug(candidate).orElse(null);
            if (existing == null || (currentCategoryId != null && currentCategoryId.equals(existing.getId()))) {
                return candidate;
            }
            candidate = base + "-" + suffix++;
        }
    }

    private String toSlug(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        String slug = normalized
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        return StringUtils.hasText(slug) ? slug : "category";
    }
}
