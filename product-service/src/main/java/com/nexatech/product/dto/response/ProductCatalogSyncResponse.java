package com.nexatech.product.dto.response;

import com.nexatech.product.model.Product;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ProductCatalogSyncResponse {

    private String id;
    private String name;
    private String slug;
    private String description;
    private String primaryCategoryId;
    private String primaryCategorySlug;
    private List<String> categoryIds;
    private List<String> categorySlugs;
    private String brandId;
    private BigDecimal price;
    private Map<String, String> specs;
    private boolean active;
    private Instant updatedAt;

    public static ProductCatalogSyncResponse from(Product product) {
        return ProductCatalogSyncResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .primaryCategoryId(product.getPrimaryCategoryId())
                .primaryCategorySlug(product.getPrimaryCategorySlug())
                .categoryIds(product.getCategoryIds())
                .categorySlugs(product.getCategorySlugs())
                .brandId(product.getBrandId())
                .price(product.getPrice())
                .specs(product.getSpecs())
                .active(product.isActive())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
