package com.nexatech.order.service;

import com.nexatech.order.client.PaymentClient;
import com.nexatech.order.client.dto.CreatePaymentDto;
import com.nexatech.order.client.dto.PaymentInfo;
import com.nexatech.order.client.dto.VNPayCreateDto;
import com.nexatech.order.client.dto.VNPayInfo;
import com.nexatech.order.dto.*;
import com.nexatech.order.dto.response.ApiResponse;
import com.nexatech.order.enums.PaymentMethod;
import com.nexatech.order.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final CartService cartService;
    private final OrderService orderService;
    private final PaymentClient paymentClient;

    /**
     * Checkout flow:
     *   1. Resolve cart items: use items from request (frontend/localStorage cart) if provided,
     *      otherwise load from Redis (requires logged-in user with customerId)
     *   2. Validate cart not empty
     *   3. Tạo đơn hàng (OrderService xử lý check stock + reserve)
     *   4. Tạo thanh toán (PaymentClient gọi payment-service)
     *   5. Nếu VNPay → tạo URL redirect
     *   6. Xóa cart sau khi checkout thành công (chỉ khi customerId != null)
     */
    public CheckoutResponse checkout(UUID customerId, String customerEmail, CheckoutRequest request) {
        // 1. Resolve cart items
        List<CreateOrderRequest.OrderItemRequest> resolvedItems;

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            // Frontend cart (localStorage) — available for both guest and logged-in users
            resolvedItems = request.getItems().stream()
                    .map(i -> {
                        var item = new CreateOrderRequest.OrderItemRequest();
                        item.setProductId(i.getProductId());
                        item.setProductName(i.getProductName());
                        item.setQuantity(i.getQuantity());
                        item.setUnitPrice(i.getUnitPrice());
                        return item;
                    })
                    .collect(Collectors.toList());
        } else if (customerId != null) {
            // Fallback: load Redis cart for logged-in user
            CartDto cart = cartService.loadCart(customerId);
            if (cart.getItems() == null || cart.getItems().isEmpty()) {
                throw new BadRequestException("Giỏ hàng trống, không thể thanh toán");
            }
            resolvedItems = cart.getItems().stream()
                    .map(cartItem -> {
                        var item = new CreateOrderRequest.OrderItemRequest();
                        item.setProductId(cartItem.getProductId());
                        item.setQuantity(cartItem.getQuantity());
                        return item;
                    })
                    .collect(Collectors.toList());
        } else {
            throw new BadRequestException("Không có sản phẩm trong giỏ hàng để thanh toán");
        }

        // 2. Build CreateOrderRequest
        CreateOrderRequest orderRequest = buildOrderRequest(resolvedItems, request);

        // 3. Tạo đơn hàng (stock validation + reserve ở trong OrderService)
        OrderResponse order = orderService.createOrder(customerId, orderRequest);

        // 4. Tạo thanh toán
        UUID paymentId = null;
        String paymentUrl = null;

        if (request.getPaymentMethod() == PaymentMethod.VNPAY) {
            VNPayInfo vnpay = paymentClient.createVNPayPayment(customerId,
                    VNPayCreateDto.builder()
                            .orderId(order.getId())
                            .amount(order.getTotalAmount())
                            .orderInfo("Thanh toan don hang " + order.getOrderNumber())
                            .build());
            if (vnpay == null || vnpay.getPaymentId() == null || vnpay.getPaymentUrl() == null) {
                compensateOrderAfterPaymentFailure(customerId, order.getId(), "VNPay payment URL creation failed");
            }
            paymentId = vnpay.getPaymentId();
            paymentUrl = vnpay.getPaymentUrl();
        } else {
            ApiResponse<PaymentInfo> paymentResp = paymentClient.createPayment(customerId,
                    "checkout-payment-" + order.getId(),
                    CreatePaymentDto.builder()
                            .orderId(order.getId())
                            .amount(order.getTotalAmount())
                            .method(request.getPaymentMethod().name())
                            .build());
            if (paymentResp == null || !paymentResp.isSuccess() || paymentResp.getData() == null) {
                compensateOrderAfterPaymentFailure(customerId, order.getId(), "Payment creation failed");
            }
            paymentId = paymentResp.getData().getId();

            // COD orders: auto-confirm immediately (no upfront payment needed)
            // This triggers ORDER_STATUS_UPDATED:CONFIRMED → shipping-service creates GHN shipment
            if (request.getPaymentMethod() == PaymentMethod.COD) {
                try {
                    orderService.updateOrderStatus(order.getId(), com.nexatech.order.enums.OrderStatus.CONFIRMED);
                    log.info("COD order auto-confirmed: orderId={}", order.getId());
                } catch (Exception e) {
                    log.error("Failed to auto-confirm COD order {}: {}", order.getId(), e.getMessage());
                }
            }
        }

        // 5. Xóa cart (chỉ khi đăng nhập — Redis cart)
        if (customerId != null) {
            cartService.clearCart(customerId);
        }

        log.info("Checkout thành công: order={}, payment={}, method={}",
                order.getOrderNumber(), paymentId, request.getPaymentMethod());

        return CheckoutResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .paymentId(paymentId)
                .paymentMethod(request.getPaymentMethod())
                .totalAmount(order.getTotalAmount())
                .shippingFee(order.getShippingFee())
                .discount(order.getDiscount())
                .paymentUrl(paymentUrl)
                .build();
    }

    private CreateOrderRequest buildOrderRequest(List<CreateOrderRequest.OrderItemRequest> items,
                                                 CheckoutRequest request) {
        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setItems(items);
        orderRequest.setShippingAddress(request.getShippingAddress());
        orderRequest.setShippingMethod(request.getShippingMethod());
        orderRequest.setPaymentMethod(request.getPaymentMethod());
        orderRequest.setNote(request.getNote());
        orderRequest.setGuestName(request.getGuestName());
        orderRequest.setGuestEmail(resolveRecipientEmail(request.getGuestEmail()));
        orderRequest.setGuestPhone(request.getGuestPhone());
        return orderRequest;
    }

    private String resolveRecipientEmail(String requestEmail) {
        if (StringUtils.hasText(requestEmail)) {
            return requestEmail.trim();
        }
        return null;
    }

    private void compensateOrderAfterPaymentFailure(UUID customerId, UUID orderId, String reason) {
        try {
            orderService.cancelOrder(customerId, orderId);
        } catch (Exception e) {
            log.error("Unable to compensate checkout after payment failure, orderId={} error={}", orderId, e.getMessage());
        }
        throw new BadRequestException(reason);
    }
}
