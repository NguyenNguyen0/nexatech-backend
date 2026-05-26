package com.nexatech.product.command.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CreateProductCommand(
        String name,
        String description,
        String descriptionHtml,
        BigDecimal price,
        BigDecimal compareAtPrice,
        String sku,
        String thumbnail,
        String primaryCategoryId,
        String primaryCategorySlug,
        List<String> categoryIds,
        List<String> categorySlugs,
        String brandId,
        List<String> images,
        Map<String, String> specs,
        List<String> tags,
        Map<String, String> metadata) {}
