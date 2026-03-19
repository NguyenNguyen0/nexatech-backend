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
     * Khi đơn hàng chuyển sang trạng thái CONFIRMED → xác nhận thanh toán thành công.
     */
    @KafkaListener(topics = "order.events", groupId = "payment-service-group")
    public void handleOrderEvent(String message) {
        try {
            OrderEventDto event = objectMapper.readValue(message, OrderEventDto.class);
            log.info("Nhận OrderEvent: type={}, orderId={}, status={}",
                    event.getEventType(), event.getOrderId(), event.getStatus());

            // Chỉ xử lý khi admin xác nhận đơn hàng → tự động hoàn tất thanh toán
            if ("ORDER_STATUS_UPDATED".equals(event.getEventType())
                    && "CONFIRMED".equals(event.getStatus())) {
                paymentService.confirmPayment(event.getOrderId());
            }
        } catch (Exception e) {
            // Log lỗi và bỏ qua message — tránh vòng lặp re-consume vô hạn
            log.error("Lỗi xử lý OrderEvent: {} | message: {}", e.getMessage(), message);
        }
    }
}
