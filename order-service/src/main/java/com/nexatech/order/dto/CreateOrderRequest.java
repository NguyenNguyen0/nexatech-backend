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
    @Pattern(
            regexp = "^(0|\\+84)(3|5|7|8|9)\\d{8}$",
            message = "Số điện thoại nhận hàng không hợp lệ. Vui lòng nhập số di động Việt Nam, ví dụ 0912345678")
    private String guestPhone;

    @Data
    public static class OrderItemRequest {

        @NotBlank
        private String productId;
        private String productName;

        @Min(1)
        private int quantity;

        /** Giá đơn vị từ frontend (localStorage cart). Dùng làm fallback khi product-service không khả dụng. */
        private BigDecimal unitPrice;
    }
}
