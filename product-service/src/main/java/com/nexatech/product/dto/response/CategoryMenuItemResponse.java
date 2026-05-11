package com.nexatech.product.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class CategoryMenuItemResponse {

    private String id;
    private String name;
    private String slug;
    private int sortOrder;
    private Map<String, Object> metadata;
    private List<CategoryMenuItemResponse> children;
}
