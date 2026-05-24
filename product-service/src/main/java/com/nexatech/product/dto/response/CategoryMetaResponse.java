package com.nexatech.product.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class CategoryMetaResponse {

    private String id;
    private String slug;
    private String name;
    private String title;
    private String description;
    private String bannerImage;
    private long productCount;
    private int subcategoryCount;
    private List<String> filterableSpecs;
    private Map<String, Object> metadata;
}
