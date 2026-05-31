package com.nexatech.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefundBankInfoRequest {

    @NotBlank(message = "Bank name is required")
    private String bankName;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotBlank(message = "Account holder is required")
    private String accountHolder;
}
