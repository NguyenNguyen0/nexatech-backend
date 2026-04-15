package com.nexatech.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddToCartRequest {

    @NotBlank
    private String productId;

    @Min(1)
    private int quantity;
}
