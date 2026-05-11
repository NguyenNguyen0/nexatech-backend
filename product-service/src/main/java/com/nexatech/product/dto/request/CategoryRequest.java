package com.nexatech.product.dto.request;

import com.nexatech.product.model.CategoryFilter;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class CategoryRequest {

    @NotBlank
    private String name;

    private String description;

    private String parentId;

    private String imageUrl;

    private int sortOrder;

    private CategoryFilter filter;

    private Map<String, Object> metadata;
}
