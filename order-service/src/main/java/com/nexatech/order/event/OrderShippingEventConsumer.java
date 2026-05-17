package com.nexatech.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.order.dto.UpdateShippingRequest;
import com.nexatech.order.dto.event.ShippingEventDto;
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
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Lắng nghe sự kiện từ shipping-service (topic: shipping.events).
 *
 * Xử lý:
 *   SHIPMENT_CREATED        → cập nhật trackingNumber + carrierName vào đơn hàng
 *   SHIPMENT_STATUS_UPDATED → nếu status=DELIVERED, cập nhật đơn hàng sang DELIVERED
 *
 * Deduplication: kiểm tra eventId trong processed_events trước khi xử lý.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderShippingEventConsumer {

    private static final String CONSUMER_NAME = "order-service:OrderShippingEventConsumer";

    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final AuditLogService auditLogService;

    @Transactional
    @KafkaListener(topics = "shipping.events", groupId = "order-service-group")
    public void handleShippingEvent(String message) {
        ShippingEventDto event;
        try {
            event = objectMapper.readValue(message, ShippingEventDto.class);
        } catch (Exception e) {
            log.error("Không thể parse ShippingEvent: {} | message: {}", e.getMessage(), message);
            throw new RuntimeException("Không thể parse ShippingEvent: " + e.getMessage(), e);
        }

        log.info("Nhận ShippingEvent: type={}, orderId={}, trackingCode={}",
                event.getEventType(), event.getOrderId(), event.getTrackingCode());

        // --- Deduplication: bỏ qua nếu đã xử lý ---
        UUID eventId = event.getEventId();
        if (eventId != null && processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
            log.info("Duplicate ShippingEvent bị bỏ qua: eventId={} type={}", eventId, event.getEventType());
            return;
        }

        if (!StringUtils.hasText(event.getOrderId()) || !StringUtils.hasText(event.getEventType())) {
            log.warn("ShippingEvent thiếu orderId hoặc eventType, bỏ qua");
            return;
        }

        UUID orderId;
        try {
            orderId = UUID.fromString(event.getOrderId());
        } catch (IllegalArgumentException e) {
            log.error("ShippingEvent có orderId không hợp lệ: {}", event.getOrderId());
            return;
        }

        // --- Xử lý nghiệp vụ ---
        try {
            switch (event.getEventType()) {
                case "SHIPMENT_CREATED" -> handleShipmentCreated(orderId, event);
                case "SHIPMENT_STATUS_UPDATED" -> handleShipmentStatusUpdated(orderId, event);
                case "SHIPMENT_CANCELLED" ->
                        log.info("Shipment CANCELLED cho order {}, không cần cập nhật thêm", orderId);
                default -> log.debug("Bỏ qua ShippingEvent không xử lý: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Lỗi xử lý ShippingEvent type={} orderId={}: {}", event.getEventType(), orderId, e.getMessage());
            throw new RuntimeException("Lỗi xử lý ShippingEvent type=" + event.getEventType()
                    + " orderId=" + orderId, e);
        }

        // --- Lưu processed event (trong cùng transaction) ---
        if (eventId != null) {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(event.getEventType())
                    .consumerName(CONSUMER_NAME)
                    .aggregateId(orderId.toString())
                    .build());
        }
    }

    private void handleShipmentCreated(UUID orderId, ShippingEventDto event) {
        if (!StringUtils.hasText(event.getTrackingCode())) {
            log.warn("SHIPMENT_CREATED event thiếu trackingCode, bỏ qua cập nhật tracking cho order {}", orderId);
            return;
        }

        UpdateShippingRequest request = new UpdateShippingRequest();
        request.setTrackingNumber(event.getTrackingCode());
        request.setCarrierName(resolveCarrierName(event.getProvider()));
        request.setShippingFee(event.getShippingFee());
        // estimatedDelivery không có trong ShippingEvent, để null

        orderService.updateShipping(orderId, request);
        orderService.publishShipmentCreatedEvent(orderId);
        auditLogService.log("SHIPMENT_CREATED_RECEIVED", "Order", orderId.toString(),
                "trackingNumber=" + event.getTrackingCode(), CONSUMER_NAME);
        log.info("Cập nhật tracking cho order {}: carrier={}, code={}",
                orderId, request.getCarrierName(), event.getTrackingCode());
    }

    private void handleShipmentStatusUpdated(UUID orderId, ShippingEventDto event) {
        if ("DELIVERED".equals(event.getStatus())) {
            orderService.updateOrderStatus(orderId, OrderStatus.DELIVERED);
            auditLogService.log("SHIPMENT_DELIVERED_RECEIVED", "Order", orderId.toString(),
                    "status=DELIVERED", CONSUMER_NAME);
            log.info("Order {} cập nhật sang DELIVERED từ shipping event", orderId);
        }
    }

    private String resolveCarrierName(String provider) {
        if (provider == null) return "Unknown";
        return switch (provider.toUpperCase()) {
            case "GHN" -> "Giao Hàng Nhanh (GHN)";
            case "MOCK" -> "NexaTech Mock Delivery";
            default -> provider;
        };
    }
}
