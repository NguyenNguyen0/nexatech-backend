package com.nexatech.order.dto;

import com.nexatech.order.enums.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    @NotNull
    private List<OrderItemRequest> items;

    @NotBlank
    private String shippingAddress;

    private String shippingMethod = "STANDARD";

    private PaymentMethod paymentMethod;

    private String note;

    // Guest contact info — passed through from CheckoutRequest
    private String guestName;
    private String guestEmail;
    private String guestPhone;

    @Data
    public static class OrderItemRequest {

        @NotBlank
        private String productId;

        @Min(1)
        private int quantity;
    }
}
