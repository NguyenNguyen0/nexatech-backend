package com.nexatech.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReference {

    private String productId;
    private String slug;
    private String name;
    private String thumbnail;
    private BigDecimal price;
}
