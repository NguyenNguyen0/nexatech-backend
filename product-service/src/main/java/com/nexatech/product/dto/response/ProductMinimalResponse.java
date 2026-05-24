package com.nexatech.product.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ProductMinimalResponse {

    private String id;
    private String name;
    private String slug;
    private String sku;

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