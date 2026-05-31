package com.nexatech.order.dto;

import com.nexatech.order.enums.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
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
    @Pattern(
            regexp = "^(0|\\+84)(3|5|7|8|9)\\d{8}$",
            message = "Số điện thoại nhận hàng không hợp lệ. Vui lòng nhập số di động Việt Nam, ví dụ 0912345678")
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
        private String productName;
        @Min(1)
        private int quantity;
        /** Giá đơn vị từ frontend (localStorage cart). Dùng làm fallback khi product-service không khả dụng. */
        private BigDecimal unitPrice;
    }
}
