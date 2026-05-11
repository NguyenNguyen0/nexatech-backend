package com.nexatech.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSuggestResponse {
    private String id;
    private String name;
    private String slug;
    private String thumbnail;
    private BigDecimal price;
}
