package com.nexatech.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.payment.dto.event.OrderEventDto;
import com.nexatech.payment.model.ProcessedEvent;
import com.nexatech.payment.repository.ProcessedEventRepository;
import com.nexatech.payment.service.AuditLogService;
import com.nexatech.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Lắng nghe sự kiện từ order-service (topic: order.events).
 *
 * Xử lý:
 *   ORDER_STATUS_UPDATED + CONFIRMED → xác nhận thanh toán (non-COD)
 *   ORDER_STATUS_UPDATED + DELIVERED → xác nhận thanh toán COD
 *   ORDER_RETURN_APPROVED            → xử lý hoàn tiền
 *
 * Deduplication: kiểm tra eventId trong processed_events trước khi xử lý.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private static final String CONSUMER_NAME = "payment-service:PaymentEventConsumer";

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final AuditLogService auditLogService;

    @Transactional
    @KafkaListener(topics = "order.events", groupId = "payment-service-group")
    public void handleOrderEvent(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        OrderEventDto event;
        try {
            event = objectMapper.readValue(message, OrderEventDto.class);
        } catch (Exception e) {
            log.error("[KAFKA][{}] partition={} offset={} key={} parseError={} message={}",
                    topic,
                    partition,
                    offset,
                    key,
                    e.getMessage(),
                    message);
            return;
        }

        log.info("Nhận OrderEvent: type={}, orderId={}, status={}",
                event.getEventType(), event.getOrderId(), event.getStatus());

        // --- Deduplication ---
        UUID eventId = event.getEventId();
        if (eventId == null) {
            log.warn("OrderEvent thiếu eventId, bỏ qua deduplication: type={}", event.getEventType());
        } else if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
            log.info("Duplicate OrderEvent bị bỏ qua: eventId={} type={}", eventId, event.getEventType());
            return;
        }

        // --- Xử lý nghiệp vụ ---
        try {
            if ("ORDER_STATUS_UPDATED".equals(event.getEventType())) {
                if ("CONFIRMED".equals(event.getStatus())) {
                    paymentService.confirmPayment(event.getOrderId());
                    auditLogService.logEvent("ORDER_CONFIRMED_RECEIVED", "Payment",
                            event.getOrderId().toString(),
                            Map.of("orderId", event.getOrderId().toString(),
                                    "triggeredBy", "kafka:order.events",
                                    "action", "confirmPayment"),
                            CONSUMER_NAME);
                } else if ("DELIVERED".equals(event.getStatus())) {
                    paymentService.confirmCodPayment(event.getOrderId());
                    auditLogService.logEvent("ORDER_DELIVERED_RECEIVED", "Payment",
                            event.getOrderId().toString(),
                            Map.of("orderId", event.getOrderId().toString(),
                                    "triggeredBy", "kafka:order.events",
                                    "action", "confirmCodPayment"),
                            CONSUMER_NAME);
                }
            } else if ("ORDER_RETURN_APPROVED".equals(event.getEventType())) {
                paymentService.processReturnRefund(event.getOrderId());
                auditLogService.logEvent("RETURN_APPROVED_RECEIVED", "Payment",
                        event.getOrderId().toString(),
                        Map.of("orderId", event.getOrderId().toString(),
                                "triggeredBy", "kafka:order.events",
                                "action", "processReturnRefund"),
                        CONSUMER_NAME);
            } else if ("ORDER_CANCELLED".equals(event.getEventType())) {
                paymentService.refundOnOrderCancellation(event.getOrderId());
                auditLogService.logEvent("ORDER_CANCELLED_RECEIVED", "Payment",
                        event.getOrderId().toString(),
                        Map.of("orderId", event.getOrderId().toString(),
                                "triggeredBy", "kafka:order.events",
                                "action", "refundOnOrderCancellation"),
                        CONSUMER_NAME);
            } else if ("ORDER_OUT_OF_STOCK".equals(event.getEventType())
                    || "ORDER_SHIPPING_FAILED".equals(event.getEventType())
                    || "ORDER_REFUND_PENDING".equals(event.getEventType())) {
                paymentService.refundOnOrderCancellation(event.getOrderId());
                auditLogService.logEvent("ORDER_COMPENSATION_RECEIVED", "Payment",
                        event.getOrderId().toString(),
                        Map.of("orderId", event.getOrderId().toString(),
                                "eventType", event.getEventType(),
                                "triggeredBy", "kafka:order.events",
                                "action", "refundOnOrderCancellation"),
                        CONSUMER_NAME);
            }
        } catch (Exception e) {
            log.error("Lỗi xử lý OrderEvent type={} orderId={}: {}", event.getEventType(), event.getOrderId(), e.getMessage());
            throw new RuntimeException("Lỗi xử lý OrderEvent type=" + event.getEventType()
                    + " orderId=" + event.getOrderId(), e);
        }

        // --- Lưu processed event (trong cùng transaction) ---
        if (eventId != null) {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(event.getEventType())
                    .consumerName(CONSUMER_NAME)
                    .aggregateId(event.getOrderId() != null ? event.getOrderId().toString() : null)
                    .build());
        }
    }
}
