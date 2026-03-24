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

    @NotNull
    @Positive
    private BigDecimal price;

    private BigDecimal compareAtPrice;

    private String sku;

    private String categoryId;

    private String brandId;

    private List<String> images;

    private Map<String, String> attributes;
}
