package com.nexatech.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WarehouseUpdateRequest {

    @NotBlank(message = "Warehouse location must not be blank")
    private String warehouseLocation;

    @Min(value = 0, message = "Reorder level must be at least 0")
    private int reorderLevel;
}
