package com.nexatech.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class StockCheckRequest {

    @NotEmpty
    @Valid
    private List<Item> items;
    private String reference;
    private String reason;

    @Data
    public static class Item {
        @NotBlank
        private String productId;

        @Min(1)
        private int quantity;
    }
}
