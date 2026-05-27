package com.nexatech.chat.client.dto;

import lombok.Data;

@Data
public class UserServiceApiResponse<T> {
    private int code;
    private String message;
    private T result;
}
