package com.nexatech.product.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ProductResponse {

    private String id;
    private String name;
    private String slug;
    private String description;
    private String descriptionHtml;
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private String sku;
    private String thumbnail;
    private String categoryId;
    private String brandId;
    private String brandName;
    private List<String> images;
    private Map<String, String> specs;
    private List<String> tags;
    private Map<String, String> attributes;
    private Map<String, String> metadata;
    private boolean active;
    private Double averageRating;
    private Instant createdAt;
    private Instant updatedAt;
}
