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
    public ApiResponse<PageResponse<ProductResponse>> getProducts(Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(productService.getProducts(pageable)));
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<ProductResponse>> searchProducts(
            @RequestParam String keyword,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(productService.searchProducts(keyword, pageable)));
    }

    @GetMapping("/category/{categoryId}")
    public ApiResponse<PageResponse<ProductResponse>> getProductsByCategory(
            @PathVariable String categoryId,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(productService.getProductsByCategory(categoryId, pageable)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ApiResponse.ok(null);
    }
}
