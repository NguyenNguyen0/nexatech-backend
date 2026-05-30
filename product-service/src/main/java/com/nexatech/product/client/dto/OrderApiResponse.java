package com.nexatech.product.client.dto;

import lombok.Data;

@Data
public class OrderApiResponse<T> {

    private boolean success;
    private T data;
    private String message;
}
