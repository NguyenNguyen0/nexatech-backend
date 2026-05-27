package com.nexatech.product.query.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Query cho GET /api/v1/products/search — keyword là bắt buộc,
 * sử dụng MongoDB text index + regex fallback.
 */
public record SearchProductsQuery(
        String keyword,
        String categoryId,
        String categorySlug,
        String brandId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double minRating,
        Double maxRating,
        Map<String, List<String>> specsFilters) {}
