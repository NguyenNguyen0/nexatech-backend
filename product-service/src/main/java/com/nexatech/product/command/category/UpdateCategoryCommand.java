package com.nexatech.product.command.category;

import com.nexatech.product.model.CategoryFilter;

import java.util.Map;

public record UpdateCategoryCommand(
        String id,
        String name,
        String description,
        String parentId,
        int sortOrder,
        CategoryFilter filter,
        Map<String, Object> metadata) {}
