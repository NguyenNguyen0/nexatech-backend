package com.nexatech.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request tạo URL thanh toán VNPay.
 */
@Data
public class VNPayPaymentRequest {

    @NotNull
    private UUID orderId;

    @NotNull
    @Positive
    private BigDecimal amount;

    /** Mô tả đơn hàng hiển thị trên trang thanh toán VNPay. */
    @NotBlank
    private String orderInfo;

    /** Địa chỉ IP của khách hàng — VNPay yêu cầu bắt buộc. */
    private String ipAddress;
}
