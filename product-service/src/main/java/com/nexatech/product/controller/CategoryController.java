package com.nexatech.product.controller;

import com.nexatech.product.dto.request.CategoryRequest;
import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.CategoryMenuItemResponse;
import com.nexatech.product.dto.response.CategoryMetaResponse;
import com.nexatech.product.dto.response.CategoryResponse;
import com.nexatech.product.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable String id,
            @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryService.updateCategory(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getCategory(@PathVariable String id) {
        return ApiResponse.ok(categoryService.getCategory(id));
    }

    @GetMapping("/slug/{slug}")
    public ApiResponse<CategoryResponse> getCategoryBySlug(@PathVariable String slug) {
        return ApiResponse.ok(categoryService.getCategoryBySlug(slug));
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAllCategories() {
        return ApiResponse.ok(categoryService.getAllCategories());
    }

    @GetMapping("/menu")
    public ApiResponse<List<CategoryMenuItemResponse>> getCategoryMenu() {
        return ApiResponse.ok(categoryService.getCategoryMenu());
    }

    @GetMapping("/{id}/subcategories")
    public ApiResponse<List<CategoryMenuItemResponse>> getSubCategories(@PathVariable String id) {
        return ApiResponse.ok(categoryService.getSubCategories(id));
    }

    @GetMapping("/slug/{slug}/subcategories")
    public ApiResponse<List<CategoryMenuItemResponse>> getSubCategoriesBySlug(@PathVariable String slug) {
        return ApiResponse.ok(categoryService.getSubCategoriesBySlug(slug));
    }

    @GetMapping("/slug/{slug}/meta")
    public ApiResponse<CategoryMetaResponse> getCategoryMetaBySlug(@PathVariable String slug) {
        return ApiResponse.ok(categoryService.getCategoryMetaBySlug(slug));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
        return ApiResponse.ok(null);
    }
}
