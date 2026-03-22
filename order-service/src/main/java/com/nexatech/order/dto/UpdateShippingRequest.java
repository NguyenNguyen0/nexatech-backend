package com.nexatech.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;

/**
 * Request cập nhật thông tin vận chuyển cho đơn hàng (admin sử dụng).
 */
@Data
public class UpdateShippingRequest {

    @NotBlank
    private String trackingNumber;

    @NotBlank
    private String carrierName;  // GHN, GHTK, VNPost, J&T, ...

    private Instant estimatedDelivery;
}
