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
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private String sku;
    private String categoryId;
    private String brandId;
    private List<String> images;
    private Map<String, String> attributes;
    private boolean active;
    private Instant createdAt;
}
