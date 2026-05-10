package com.nexatech.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ProductRequest {

    @NotBlank
    private String name;

    private String description;

    private String descriptionHtml;

    @NotNull
    @Positive
    private BigDecimal price;

    private BigDecimal compareAtPrice;

    private String sku;

    private String thumbnail;

    private String categoryId;

    private String brandId;

    private List<String> images;

    private Map<String, String> specs;

    private List<String> tags;

    private Map<String, String> attributes;

    private Map<String, String> metadata;
}
