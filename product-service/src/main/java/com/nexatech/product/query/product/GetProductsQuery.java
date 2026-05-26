package com.nexatech.product.query.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Query cho GET /api/v1/products — bao gồm tất cả filter params.
 * keyword là optional; featured=true short-circuits về getFeaturedProductsMinimal.
 */
public record GetProductsQuery(
        String keyword,
        String categoryId,
        String categorySlug,
        String brandId,
        String sku,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double minRating,
        Double maxRating,
        Boolean featured,
        Boolean active,
        boolean includeDeleted,
        Map<String, List<String>> specsFilters) {}
