package com.nexatech.product.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryResponse {

    private String id;
    private String name;
    private String slug;
    private String description;
    private String parentId;
    private String imageUrl;
    private int sortOrder;
    private boolean active;
}
