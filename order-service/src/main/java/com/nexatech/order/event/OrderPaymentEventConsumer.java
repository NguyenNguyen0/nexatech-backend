package com.nexatech.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.order.dto.event.PaymentEventDto;
import com.nexatech.order.enums.OrderStatus;
import com.nexatech.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Lắng nghe sự kiện từ payment-service (topic: payment.events).
 *
 * Xử lý:
 *   PAYMENT_REFUNDED → cập nhật đơn hàng sang REFUNDED
 *                       (sau khi admin duyệt trả hàng và payment-service hoàn tiền xong)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment.events", groupId = "order-service-group")
    public void handlePaymentEvent(String message) {
        try {
            PaymentEventDto event = objectMapper.readValue(message, PaymentEventDto.class);
            log.info("Nhận PaymentEvent: type={}, orderId={}, status={}",
                    event.getEventType(), event.getOrderId(), event.getStatus());

            if (event.getOrderId() == null) return;

            switch (event.getEventType() != null ? event.getEventType() : "") {
                case "PAYMENT_COMPLETED" ->
                    orderService.updateOrderStatus(event.getOrderId(), OrderStatus.CONFIRMED);

                case "PAYMENT_REFUNDED" ->
                    orderService.updateOrderStatus(event.getOrderId(), OrderStatus.REFUNDED);

                default -> log.debug("Bỏ qua PaymentEvent không xử lý: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Lỗi xử lý PaymentEvent: {} | message: {}", e.getMessage(), message);
        }
    }
}
