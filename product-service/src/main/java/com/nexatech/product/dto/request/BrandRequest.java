package com.nexatech.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BrandRequest {

    @NotBlank(message = "Brand name must not be blank")
    private String name;

    private String parentBrandId;

    private String logoUrl;
}
