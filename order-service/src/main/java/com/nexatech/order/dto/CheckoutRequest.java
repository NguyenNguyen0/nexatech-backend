package com.nexatech.order.dto;

import com.nexatech.order.enums.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CheckoutRequest {

    @NotBlank
    private String shippingAddress;

    private String shippingMethod = "STANDARD";

    @NotNull
    private PaymentMethod paymentMethod;

    private String note;

    private String couponCode;

    // Guest / buyer contact info (stored in order for customer service)
    private String guestName;
    private String guestEmail;
    private String guestPhone;

    /**
     * Cart items from frontend (localStorage cart). When provided, the checkout
     * service uses these directly instead of loading the Redis cart.
     * Required for guest users who have no Redis cart.
     */
    private List<ItemRequest> items;

    @Data
    public static class ItemRequest {
        @NotBlank
        private String productId;
        @Min(1)
        private int quantity;
    }
}
