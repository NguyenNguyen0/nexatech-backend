package com.nexatech.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.payment.dto.event.OrderEventDto;
import com.nexatech.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    /**
     * Lắng nghe các sự kiện từ order-service qua topic order.events.
     *
     * Xử lý:
     *   ORDER_STATUS_UPDATED + CONFIRMED  → xác nhận thanh toán (non-COD)
     *   ORDER_STATUS_UPDATED + DELIVERED  → xác nhận thanh toán COD (tiền mặt khi nhận hàng)
     *   ORDER_RETURN_APPROVED             → xử lý hoàn tiền cho đơn trả hàng
     */
    @KafkaListener(topics = "order.events", groupId = "payment-service-group")
    public void handleOrderEvent(String message) {
        try {
            OrderEventDto event = objectMapper.readValue(message, OrderEventDto.class);
            log.info("Nhận OrderEvent: type={}, orderId={}, status={}",
                    event.getEventType(), event.getOrderId(), event.getStatus());

            if ("ORDER_STATUS_UPDATED".equals(event.getEventType())) {
                if ("CONFIRMED".equals(event.getStatus())) {
                    // Đơn được xác nhận → thanh toán online (non-COD) hoàn tất
                    paymentService.confirmPayment(event.getOrderId());

                } else if ("DELIVERED".equals(event.getStatus())) {
                    // Đơn giao thành công → tự động xác nhận thanh toán COD
                    paymentService.confirmCodPayment(event.getOrderId());
                }

            } else if ("ORDER_RETURN_APPROVED".equals(event.getEventType())) {
                // Admin duyệt trả hàng → kích hoạt hoàn tiền
                paymentService.processReturnRefund(event.getOrderId());
            }

        } catch (Exception e) {
            // Log lỗi và bỏ qua message — tránh vòng lặp re-consume vô hạn
            log.error("Lỗi xử lý OrderEvent: {} | message: {}", e.getMessage(), message);
        }
    }
}
