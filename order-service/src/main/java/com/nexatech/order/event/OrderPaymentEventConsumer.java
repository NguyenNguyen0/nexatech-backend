package com.nexatech.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.order.dto.event.PaymentEventDto;
import com.nexatech.order.enums.OrderStatus;
import com.nexatech.order.model.ProcessedEvent;
import com.nexatech.order.repository.ProcessedEventRepository;
import com.nexatech.order.service.AuditLogService;
import com.nexatech.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Lắng nghe sự kiện từ payment-service (topic: payment.events).
 *
 * Xử lý:
 *   PAYMENT_COMPLETED → cập nhật đơn hàng sang CONFIRMED
 *   PAYMENT_REFUNDED  → cập nhật đơn hàng sang REFUNDED
 *
 * Deduplication: kiểm tra eventId trong processed_events trước khi xử lý.
 * Nếu Kafka re-deliver message, consumer bỏ qua một cách an toàn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentEventConsumer {

    private static final String CONSUMER_NAME = "order-service:OrderPaymentEventConsumer";

    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final AuditLogService auditLogService;

    @Transactional
    @KafkaListener(topics = "payment.events", groupId = "order-service-group")
    public void handlePaymentEvent(String message) {
        PaymentEventDto event;
        try {
            event = objectMapper.readValue(message, PaymentEventDto.class);
        } catch (Exception e) {
            log.error("Không thể parse PaymentEvent: {} | message: {}", e.getMessage(), message);
            throw new RuntimeException("Không thể parse PaymentEvent: " + e.getMessage(), e);
        }

        log.info("Nhận PaymentEvent: type={}, orderId={}, status={}",
                event.getEventType(), event.getOrderId(), event.getStatus());

        // --- Deduplication: bỏ qua nếu đã xử lý ---
        UUID eventId = event.getEventId();
        if (eventId == null) {
            log.warn("PaymentEvent thiếu eventId, bỏ qua deduplication: type={}", event.getEventType());
        } else if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
            log.info("Duplicate PaymentEvent bị bỏ qua: eventId={} type={}", eventId, event.getEventType());
            return;
        }

        if (event.getOrderId() == null) return;

        // --- Xử lý nghiệp vụ ---
        try {
            switch (event.getEventType() != null ? event.getEventType() : "") {
                case "PAYMENT_COMPLETED" -> {
                    if (orderService.hasStatus(event.getOrderId(), OrderStatus.CANCELLED)) {
                        log.warn("Late PAYMENT_COMPLETED ignored because order is already CANCELLED: orderId={}",
                                event.getOrderId());
                        auditLogService.logEvent("PAYMENT_COMPLETED_LATE_IGNORED", "Order",
                                event.getOrderId().toString(),
                                Map.of("orderId", event.getOrderId().toString(),
                                        "currentStatus", "CANCELLED",
                                        "triggeredBy", "kafka:payment.events"),
                                CONSUMER_NAME);
                        break;
                    }
                    orderService.updateOrderStatus(event.getOrderId(), OrderStatus.CONFIRMED);
                    auditLogService.logEvent("PAYMENT_COMPLETED_RECEIVED", "Order",
                            event.getOrderId().toString(),
                            Map.of("orderId", event.getOrderId().toString(),
                                    "newStatus", "CONFIRMED",
                                    "triggeredBy", "kafka:payment.events"),
                            CONSUMER_NAME);
                }
                case "PAYMENT_FAILED" -> {
                    orderService.cancelOrderForSaga(event.getOrderId(), "payment_failed", CONSUMER_NAME);
                    auditLogService.logEvent("PAYMENT_FAILED_RECEIVED", "Order",
                            event.getOrderId().toString(),
                            Map.of("orderId", event.getOrderId().toString(),
                                    "newStatus", "CANCELLED",
                                    "triggeredBy", "kafka:payment.events"),
                            CONSUMER_NAME);
                }
                case "PAYMENT_REFUNDED" -> {
                    orderService.updateOrderStatus(event.getOrderId(), OrderStatus.REFUNDED);
                    auditLogService.logEvent("PAYMENT_REFUNDED_RECEIVED", "Order",
                            event.getOrderId().toString(),
                            Map.of("orderId", event.getOrderId().toString(),
                                    "newStatus", "REFUNDED",
                                    "triggeredBy", "kafka:payment.events"),
                            CONSUMER_NAME);
                }
                default -> log.debug("Bỏ qua PaymentEvent không xử lý: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Lỗi xử lý PaymentEvent type={} orderId={}: {}", event.getEventType(), event.getOrderId(), e.getMessage());
            throw new RuntimeException("Lỗi xử lý PaymentEvent type=" + event.getEventType()
                    + " orderId=" + event.getOrderId(), e);
        }

        // --- Lưu processed event (trong cùng transaction) ---
        if (eventId != null) {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(event.getEventType())
                    .consumerName(CONSUMER_NAME)
                    .aggregateId(event.getOrderId().toString())
                    .build());
        }
    }
}
