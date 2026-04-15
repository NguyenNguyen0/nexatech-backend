package com.nexatech.order.dto;

import com.nexatech.order.enums.PaymentMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class CheckoutResponse {

    private UUID orderId;
    private String orderNumber;
    private UUID paymentId;
    private PaymentMethod paymentMethod;
    private BigDecimal totalAmount;
    private BigDecimal shippingFee;
    private BigDecimal discount;

    // VNPay: URL redirect để thanh toán; COD/BANK_TRANSFER: null
    private String paymentUrl;
}
