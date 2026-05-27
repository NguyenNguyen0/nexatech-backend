package com.nexatech.product.query.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record GetProductsByCategorySlugQuery(
        String slug,
        String brandId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double minRating,
        Double maxRating,
        Boolean featured,
        Map<String, List<String>> specsFilters) {}
