package com.nexatech.product.controller;

import com.nexatech.product.dto.request.ProductRequest;
import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.PageResponse;
import com.nexatech.product.dto.response.ProductResponse;
import com.nexatech.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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

    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> getProducts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating,
            @RequestParam(required = false) Boolean featured,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
                productService.getProducts(categoryId, brandId, minPrice, maxPrice, minRating, maxRating, featured, pageable)));
    }

    @GetMapping("/featured")
    public ApiResponse<PageResponse<ProductResponse>> getFeaturedProducts(Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(productService.getFeaturedProducts(pageable)));
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<ProductResponse>> searchProducts(
            @RequestParam String keyword,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
                productService.searchProducts(keyword, categoryId, brandId, minPrice, maxPrice, minRating, maxRating, pageable)));
    }

    @GetMapping("/suggest")
    public ApiResponse<List<String>> suggestProducts(@RequestParam String q) {
        return ApiResponse.ok(productService.suggestProducts(q));
    }

    @GetMapping("/category/{categoryId}")
    public ApiResponse<PageResponse<ProductResponse>> getProductsByCategory(
            @PathVariable String categoryId,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(productService.getProductsByCategory(categoryId, pageable)));
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
}
