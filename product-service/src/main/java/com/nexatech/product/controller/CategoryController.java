package com.nexatech.product.controller;

import com.nexatech.product.command.category.CreateCategoryCommand;
import com.nexatech.product.command.category.DeleteCategoryCommand;
import com.nexatech.product.command.category.RestoreCategoryCommand;
import com.nexatech.product.command.category.UpdateCategoryCommand;
import com.nexatech.product.constant.PermissionsAccept;
import com.nexatech.product.dto.request.CategoryRequest;
import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.CategoryMenuItemResponse;
import com.nexatech.product.dto.response.CategoryMetaResponse;
import com.nexatech.product.dto.response.CategoryResponse;
import com.nexatech.product.handler.category.CategoryCommandHandler;
import com.nexatech.product.handler.category.CategoryQueryHandler;
import com.nexatech.product.query.category.GetAllCategoriesQuery;
import com.nexatech.product.query.category.GetCategoryByIdQuery;
import com.nexatech.product.query.category.GetCategoryBySlugQuery;
import com.nexatech.product.query.category.GetCategoryMenuQuery;
import com.nexatech.product.query.category.GetCategoryMetaBySlugQuery;
import com.nexatech.product.query.category.GetSubCategoriesBySlugQuery;
import com.nexatech.product.query.category.GetSubCategoriesQuery;
import com.nexatech.product.security.RequiresPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryCommandHandler categoryCommandHandler;
    private final CategoryQueryHandler categoryQueryHandler;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    // @RequiresPermission(PermissionsAccept.Category.WRITE)
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryCommandHandler.handle(toCreateCommand(request)));
    }

    @PutMapping("/{id}")
    // @RequiresPermission(PermissionsAccept.Category.WRITE)
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable String id,
            @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryCommandHandler.handle(toUpdateCommand(id, request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getCategory(@PathVariable String id) {
        return ApiResponse.ok(categoryQueryHandler.handle(new GetCategoryByIdQuery(id)));
    }

    @GetMapping("/slug/{slug}")
    public ApiResponse<CategoryResponse> getCategoryBySlug(@PathVariable String slug) {
        return ApiResponse.ok(categoryQueryHandler.handle(new GetCategoryBySlugQuery(slug)));
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAllCategories(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeleted) {
        return ApiResponse.ok(categoryQueryHandler.handle(new GetAllCategoriesQuery(active, includeDeleted)));
    }

    @GetMapping("/menu")
    public ApiResponse<List<CategoryMenuItemResponse>> getCategoryMenu() {
        return ApiResponse.ok(categoryQueryHandler.handle(new GetCategoryMenuQuery()));
    }

    @GetMapping("/{id}/subcategories")
    public ApiResponse<List<CategoryMenuItemResponse>> getSubCategories(@PathVariable String id) {
        return ApiResponse.ok(categoryQueryHandler.handle(new GetSubCategoriesQuery(id)));
    }

    @GetMapping("/slug/{slug}/subcategories")
    public ApiResponse<List<CategoryMenuItemResponse>> getSubCategoriesBySlug(@PathVariable String slug) {
        return ApiResponse.ok(categoryQueryHandler.handle(new GetSubCategoriesBySlugQuery(slug)));
    }

    @GetMapping("/slug/{slug}/meta")
    public ApiResponse<CategoryMetaResponse> getCategoryMetaBySlug(@PathVariable String slug) {
        return ApiResponse.ok(categoryQueryHandler.handle(new GetCategoryMetaBySlugQuery(slug)));
    }

    @DeleteMapping("/{id}")
    // @RequiresPermission(PermissionsAccept.Category.WRITE)
    public ApiResponse<Void> deleteCategory(@PathVariable String id) {
        categoryCommandHandler.handle(new DeleteCategoryCommand(id));
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/restore")
    // @RequiresPermission(PermissionsAccept.Category.WRITE)
    public ApiResponse<CategoryResponse> restoreCategory(@PathVariable String id) {
        return ApiResponse.ok(categoryCommandHandler.handle(new RestoreCategoryCommand(id)));
    }

    private CreateCategoryCommand toCreateCommand(CategoryRequest request) {
        return new CreateCategoryCommand(
                request.getName(),
                request.getDescription(),
                request.getParentId(),
                request.getSortOrder(),
                request.getFilter(),
                request.getMetadata());
    }

    private UpdateCategoryCommand toUpdateCommand(String id, CategoryRequest request) {
        return new UpdateCategoryCommand(
                id,
                request.getName(),
                request.getDescription(),
                request.getParentId(),
                request.getSortOrder(),
                request.getFilter(),
                request.getMetadata());
    }
}
