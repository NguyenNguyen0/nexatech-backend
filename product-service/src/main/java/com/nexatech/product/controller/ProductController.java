package com.nexatech.product.controller;

import com.nexatech.product.dto.request.ProductRequest;
import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.PageResponse;
import com.nexatech.product.dto.response.ProductResponse;
import com.nexatech.product.dto.response.ProductSuggestResponse;
import com.nexatech.product.service.ProductService;
import com.nexatech.product.util.TextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(productService.createProduct(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(productService.updateProduct(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getProduct(@PathVariable String id) {
        return ApiResponse.ok(productService.getProduct(id));
    }

    @GetMapping("/slug/{slug}")
    public ApiResponse<ProductResponse> getProductBySlug(@PathVariable String slug) {
        return ApiResponse.ok(productService.getProductBySlug(slug));
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> getProducts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) String brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating,
            @RequestParam(required = false) Boolean featured,
            @RequestParam MultiValueMap<String, String> queryParams,
            Pageable pageable) {
        Map<String, List<String>> specsFilters = extractSpecsFilters(queryParams);
        return ApiResponse.ok(PageResponse.of(
                productService.getProducts(
                        categoryId,
                        categorySlug,
                        brandId,
                        minPrice,
                        maxPrice,
                        minRating,
                        maxRating,
                        featured,
                        specsFilters,
                        pageable)));
    }

    @GetMapping("/featured")
    public ApiResponse<PageResponse<ProductResponse>> getFeaturedProducts(Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(productService.getFeaturedProducts(pageable)));
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<ProductResponse>> searchProducts(
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
        return ApiResponse.ok(PageResponse.of(
                productService.searchProducts(
                        keyword,
                        categoryId,
                        categorySlug,
                        brandId,
                        minPrice,
                        maxPrice,
                        minRating,
                        maxRating,
                        specsFilters,
                        pageable)));
    }

    @GetMapping("/suggest")
    public ApiResponse<List<ProductSuggestResponse>> suggestProducts(@RequestParam String q) {
        return ApiResponse.ok(productService.suggestProducts(q));
    }

    @GetMapping("/tags")
    public ApiResponse<PageResponse<ProductResponse>> getProductsByTags(
            @RequestParam List<String> tags,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(productService.getProductsByTags(tags, pageable)));
    }

    @GetMapping("/category/{categoryId}")
    public ApiResponse<PageResponse<ProductResponse>> getProductsByCategory(
            @PathVariable String categoryId,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(productService.getProductsByCategory(categoryId, pageable)));
    }

    @GetMapping("/category/slug/{slug}")
    public ApiResponse<PageResponse<ProductResponse>> getProductsByCategorySlug(
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
        return ApiResponse.ok(PageResponse.of(
                productService.getProductsByCategorySlug(
                        slug,
                        brandId,
                        minPrice,
                        maxPrice,
                        minRating,
                        maxRating,
                        featured,
                        specsFilters,
                        pageable)));
    }

    @GetMapping("/{id}/related")
    public ApiResponse<List<ProductResponse>> getRelatedProducts(@PathVariable String id) {
        return ApiResponse.ok(productService.getRelatedProducts(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ApiResponse.ok(null);
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
