package com.nexatech.product.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

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

    @Id
    private String id;

    @TextIndexed(weight = 5)
    private String name;

    private String slug;

    @TextIndexed(weight = 2)
    private String description;

    private BigDecimal price;

    private BigDecimal compareAtPrice;

    @TextIndexed(weight = 3)
    private String sku;

    private String categoryId;

    private String brandId;

    private List<String> images;

    private Map<String, String> attributes;

    private boolean active;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
