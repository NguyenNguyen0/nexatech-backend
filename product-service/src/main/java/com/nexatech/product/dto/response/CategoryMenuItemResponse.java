package com.nexatech.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryMenuItemResponse {

    private String id;
    private String name;
    private String slug;
    private int sortOrder;
    private Map<String, Object> metadata;
    private List<CategoryMenuItemResponse> children;
}
