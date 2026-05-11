package com.nexatech.product.dto.response;

import com.nexatech.product.model.CategoryFilter;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class CategoryResponse {

    private String id;
    private String name;
    private String slug;
    private String description;
    private String parentId;
    private int sortOrder;
    private boolean active;
    private CategoryFilter filter;
    private Map<String, Object> metadata;
}
