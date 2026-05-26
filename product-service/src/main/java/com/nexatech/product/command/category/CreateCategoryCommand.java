package com.nexatech.product.command.category;

import com.nexatech.product.model.CategoryFilter;

import java.util.Map;

public record CreateCategoryCommand(
        String name,
        String description,
        String parentId,
        int sortOrder,
        CategoryFilter filter,
        Map<String, Object> metadata) {}
