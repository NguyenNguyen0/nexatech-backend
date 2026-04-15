package com.nexatech.order.dto;

import com.nexatech.order.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckoutRequest {

    @NotBlank
    private String shippingAddress;

    private String shippingMethod = "STANDARD";

    @NotNull
    private PaymentMethod paymentMethod;

    private String note;

    private String couponCode;
}
