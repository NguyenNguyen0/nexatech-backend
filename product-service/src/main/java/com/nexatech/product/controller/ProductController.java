package com.nexatech.product.controller;

import com.nexatech.product.command.product.CreateProductCommand;
import com.nexatech.product.command.product.DeleteProductCommand;
import com.nexatech.product.command.product.RestoreProductCommand;
import com.nexatech.product.command.product.UpdateProductCommand;
import com.nexatech.product.dto.request.ProductRequest;
import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.PageResponse;
import com.nexatech.product.dto.response.ProductMinimalResponse;
import com.nexatech.product.dto.response.ProductResponse;
import com.nexatech.product.dto.response.ReviewResponse;
import com.nexatech.product.handler.product.ProductCommandHandler;
import com.nexatech.product.handler.product.ProductQueryHandler;
import com.nexatech.product.handler.review.ReviewQueryHandler;
import com.nexatech.product.query.product.*;
import com.nexatech.product.query.review.GetReviewsByProductSlugQuery;
import com.nexatech.product.util.TextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductCommandHandler productCommandHandler;
    private final ProductQueryHandler productQueryHandler;
    private final ReviewQueryHandler reviewQueryHandler;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(productCommandHandler.handle(toCreateCommand(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(productCommandHandler.handle(toUpdateCommand(id, request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getProduct(@PathVariable String id) {
        return ApiResponse.ok(productQueryHandler.handle(new GetProductByIdQuery(id)));
    }

    @GetMapping("/slug/{slug}")
    public ApiResponse<ProductResponse> getProductBySlug(@PathVariable String slug) {
        return ApiResponse.ok(productQueryHandler.handle(new GetProductBySlugQuery(slug)));
    }

    @GetMapping
    @Cacheable(
            value = "productPages",
            key = "T(com.nexatech.product.util.ProductCacheKey).list('list', #keyword, #categoryId, #categorySlug, #brandId, #sku, #minPrice, #maxPrice, #minRating, #maxRating, #featured, #active, #includeDeleted, #queryParams, #pageable)",
            unless = "#result == null || #result.data == null")
    public ApiResponse<PageResponse<ProductMinimalResponse>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) String brandId,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeleted,
            @RequestParam MultiValueMap<String, String> queryParams,
            Pageable pageable) {
        Map<String, List<String>> specsFilters = extractSpecsFilters(queryParams);
        return ApiResponse.ok(PageResponse.of(productQueryHandler.handle(
                new GetProductsQuery(keyword, categoryId, categorySlug, brandId, sku,
                        minPrice, maxPrice, minRating, maxRating,
                        featured, active, includeDeleted, specsFilters),
                pageable)));
    }

    @GetMapping("/featured")
    @Cacheable(
            value = "productPages",
            key = "T(com.nexatech.product.util.ProductCacheKey).list('featured', #pageable)",
            unless = "#result == null || #result.data == null")
    public ApiResponse<PageResponse<ProductResponse>> getFeaturedProducts(Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
                productQueryHandler.handle(new GetFeaturedProductsQuery(), pageable)));
    }

    @GetMapping("/search")
    @Cacheable(
            value = "productPages",
            key = "T(com.nexatech.product.util.ProductCacheKey).list('search', #keyword, #categoryId, #categorySlug, #brandId, #minPrice, #maxPrice, #minRating, #maxRating, #queryParams, #pageable)",
            unless = "#result == null || #result.data == null")
    public ApiResponse<PageResponse<ProductMinimalResponse>> searchProducts(
            @RequestParam String keyword,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) String brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating,
            @RequestParam MultiValueMap<String, String> queryParams,
            Pageable pageable) {
        Map<String, List<String>> specsFilters = extractSpecsFilters(queryParams);
        return ApiResponse.ok(PageResponse.of(productQueryHandler.handle(
                new SearchProductsQuery(keyword, categoryId, categorySlug, brandId,
                        minPrice, maxPrice, minRating, maxRating, specsFilters),
                pageable)));
    }

    @GetMapping("/tags")
    public ApiResponse<PageResponse<ProductMinimalResponse>> getProductsByTags(
            @RequestParam List<String> tags,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
                productQueryHandler.handle(new GetProductsByTagsQuery(tags), pageable)));
    }

    @GetMapping("/category/{categoryId}")
    public ApiResponse<PageResponse<ProductResponse>> getProductsByCategory(
            @PathVariable String categoryId,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
                productQueryHandler.handle(new GetProductsByCategoryQuery(categoryId), pageable)));
    }

    @GetMapping("/category/slug/{slug}")
    @Cacheable(
            value = "productPages",
            key = "T(com.nexatech.product.util.ProductCacheKey).list('categorySlug', #slug, #brandId, #minPrice, #maxPrice, #minRating, #maxRating, #featured, #queryParams, #pageable)",
            unless = "#result == null || #result.data == null")
    public ApiResponse<PageResponse<ProductMinimalResponse>> getProductsByCategorySlug(
            @PathVariable String slug,
            @RequestParam(required = false) String brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating,
            @RequestParam(required = false) Boolean featured,
            @RequestParam MultiValueMap<String, String> queryParams,
            Pageable pageable) {
        Map<String, List<String>> specsFilters = extractSpecsFilters(queryParams);
        return ApiResponse.ok(PageResponse.of(productQueryHandler.handle(
                new GetProductsByCategorySlugQuery(slug, brandId, minPrice, maxPrice,
                        minRating, maxRating, featured, specsFilters),
                pageable)));
    }

    @GetMapping("/{id}/related")
    public ApiResponse<List<ProductMinimalResponse>> getRelatedProducts(@PathVariable String id) {
        return ApiResponse.ok(productQueryHandler.handle(new GetRelatedProductsQuery(id)));
    }

    @GetMapping("/slug/{slug}/related")
    public ApiResponse<List<ProductMinimalResponse>> getRelatedProductsBySlug(@PathVariable String slug) {
        return ApiResponse.ok(productQueryHandler.handle(new GetRelatedProductsBySlugQuery(slug)));
    }

    @GetMapping("/slug/{slug}/reviews")
    public ApiResponse<PageResponse<ReviewResponse>> getReviewsByProductSlug(
            @PathVariable String slug,
            @RequestParam(required = false) Integer rating,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
            reviewQueryHandler.handle(new GetReviewsByProductSlugQuery(slug, rating), pageable)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable String id) {
        productCommandHandler.handle(new DeleteProductCommand(id));
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/restore")
    public ApiResponse<ProductResponse> restoreProduct(@PathVariable String id) {
        return ApiResponse.ok(productCommandHandler.handle(new RestoreProductCommand(id)));
    }

    // ---------- private helpers ----------

    private CreateProductCommand toCreateCommand(ProductRequest r) {
        return new CreateProductCommand(
                r.getName(), r.getDescription(), r.getDescriptionHtml(),
                r.getPrice(), r.getCompareAtPrice(), r.getSku(), r.getThumbnail(),
                r.getPrimaryCategoryId(), r.getPrimaryCategorySlug(),
                r.getCategoryIds(), r.getCategorySlugs(), r.getBrandId(),
                r.getImages(), r.getSpecs(), r.getTags(), r.getMetadata());
    }

    private UpdateProductCommand toUpdateCommand(String id, ProductRequest r) {
        return new UpdateProductCommand(
                id, r.getName(), r.getDescription(), r.getDescriptionHtml(),
                r.getPrice(), r.getCompareAtPrice(), r.getSku(), r.getThumbnail(),
                r.getPrimaryCategoryId(), r.getPrimaryCategorySlug(),
                r.getCategoryIds(), r.getCategorySlugs(), r.getBrandId(),
                r.getImages(), r.getSpecs(), r.getTags(), r.getMetadata());
    }

    private Map<String, List<String>> extractSpecsFilters(MultiValueMap<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> specsFilters = new LinkedHashMap<>();
        queryParams.forEach((key, values) -> {
            if (!StringUtils.hasText(key) || !key.startsWith("specs.")) {
                return;
            }

            String specKey = key.substring("specs.".length()).trim();
            if (!StringUtils.hasText(specKey)) {
                return;
            }

            List<String> normalizedValues = TextUtils.normalizeDistinct(values);
            if (!normalizedValues.isEmpty()) {
                specsFilters.put(specKey, normalizedValues);
            }
        });

        return specsFilters.isEmpty() ? Map.of() : Map.copyOf(specsFilters);
    }
}
