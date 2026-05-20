package com.nexatech.product.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @MongoId(FieldType.STRING)
    private String id;

    @TextIndexed(weight = 5)
    private String name;

    private String slug;

    @TextIndexed(weight = 2)
    private String description;

    private String descriptionHtml;

    private BigDecimal price;

    private BigDecimal compareAtPrice;

    @TextIndexed(weight = 3)
    private String sku;

    private String thumbnail;

    private String primaryCategoryId;

    private String primaryCategorySlug;

    private List<String> categoryIds;

    private List<String> categorySlugs;

    private String brandId;

    private List<String> images;

    private Map<String, String> specs;

    private List<String> tags;

    private Map<String, String> metadata;

    private boolean active;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
