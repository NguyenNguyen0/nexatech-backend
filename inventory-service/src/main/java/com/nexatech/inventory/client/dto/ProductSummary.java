package com.nexatech.inventory.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSummary {

    private String id;
    private String name;
    private String slug;
    private String sku;
    private String thumbnail;
}