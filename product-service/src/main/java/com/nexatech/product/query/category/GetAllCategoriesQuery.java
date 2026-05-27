package com.nexatech.product.query.category;

public record GetAllCategoriesQuery(
        Boolean active,
        boolean includeDeleted) {}
