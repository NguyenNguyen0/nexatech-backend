package com.nexatech.product.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductMinimalResponse {

    private String id;
    private String name;
    private String slug;
    private String sku;
    private String description;

    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private BigDecimal originalPrice;

    private String thumbnail;

    private String brandId;
    private String brandName;
    private String brandSlug;

    private String primaryCategoryId;
    private String primaryCategorySlug;
    private String primaryCategoryName;
    private List<String> categoryIds;
    private List<String> categorySlugs;
    private Map<String, String> specs;

    private List<String> tags;
    private Boolean featured;

    private Double averageRating;
    private Long reviewCount;

    private Integer stockQuantity;
    private String stockStatus;

    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
