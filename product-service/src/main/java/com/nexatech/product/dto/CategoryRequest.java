package com.nexatech.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryRequest {

    @NotBlank
    private String name;

    private String description;

    private String parentId;

    private String imageUrl;

    private int sortOrder;
}
