package com.nexatech.product.client.dto;

import lombok.Data;

@Data
public class OrderPurchasedResponse {

    private boolean success;
    private Boolean data;
    private String message;
}
