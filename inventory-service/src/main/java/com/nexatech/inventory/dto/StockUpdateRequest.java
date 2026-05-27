package com.nexatech.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StockUpdateRequest {

    @NotBlank
    private String productId;

    private String sku;

    @Min(0)
    private int quantity;

    private String reason;
}
