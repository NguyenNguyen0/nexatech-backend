package com.nexatech.shipping.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.shipping.dto.CreateShipmentRequest;
import com.nexatech.shipping.dto.ShipmentItemRequest;
import com.nexatech.shipping.dto.event.OrderEventDto;
import com.nexatech.shipping.entity.ProcessedEvent;
import com.nexatech.shipping.provider.ghn.GhnProperties;
import com.nexatech.shipping.repository.ProcessedEventRepository;
import com.nexatech.shipping.service.AuditLogService;
import com.nexatech.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private static final String CONSUMER_NAME = "shipping-service:OrderEventConsumer";

    private final ShippingService shippingService;
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final AuditLogService auditLogService;
    private final GhnProperties ghnProperties;

    @Transactional
    @KafkaListener(topics = "order.events", groupId = "shipping-service-group")
    public void handleOrderEvent(String message) {
        OrderEventDto event;
        try {
            event = objectMapper.readValue(message, OrderEventDto.class);
        } catch (Exception e) {
            log.error("Unable to parse OrderEvent for shipping: {} | message={}", e.getMessage(), message);
            throw new RuntimeException("Unable to parse OrderEvent for shipping: " + e.getMessage(), e);
        }

        UUID eventId = event.getEventId();
        if (eventId == null) {
            log.warn("OrderEvent is missing eventId, deduplication skipped: type={}", event.getEventType());
        } else if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
            log.info("Duplicate OrderEvent skipped by shipping-service: eventId={} type={}",
                    eventId, event.getEventType());
            return;
        }

        if (event.getOrderId() == null) {
            return;
        }

        try {
            switch (event.getEventType() != null ? event.getEventType() : "") {
                case "ORDER_CANCELLED" -> {
                    shippingService.cancelShipmentByOrderIdIfPresent(event.getOrderId().toString(), CONSUMER_NAME);
                    auditLogService.log("ORDER_CANCELLED_RECEIVED", "Shipment", event.getOrderId().toString(),
                            "cancel shipment if present", CONSUMER_NAME);
                }
                case "ORDER_STATUS_UPDATED" -> {
                    if ("CONFIRMED".equals(event.getStatus())) {
                        auditLogService.log("ORDER_CONFIRMED_RECEIVED", "Shipment", event.getOrderId().toString(),
                                "order confirmed — creating shipment", CONSUMER_NAME);
                        createShipmentFromEvent(event);
                    }
                }
                default -> log.debug("OrderEvent ignored by shipping-service: type={}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Unable to process OrderEvent in shipping-service: type={} orderId={} error={}",
                    event.getEventType(), event.getOrderId(), e.getMessage());
            throw new RuntimeException("Unable to process OrderEvent type=" + event.getEventType()
                    + " orderId=" + event.getOrderId(), e);
        }

        if (eventId != null) {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(event.getEventType())
                    .consumerName(CONSUMER_NAME)
                    .aggregateId(event.getOrderId().toString())
                    .build());
        }
    }

    /**
     * Tạo shipment khi đơn hàng được xác nhận (ORDER_CONFIRMED).
     * Dùng district/ward của shop làm fallback vì địa chỉ free-text không map được sang GHN code.
     */
    private void createShipmentFromEvent(OrderEventDto event) {
        String customerName = event.getGuestName() != null ? event.getGuestName() : "Khách hàng";
        // GHN yêu cầu số điện thoại hợp lệ (10-11 chữ số); dùng fallback nếu không hợp lệ
        String rawPhone = event.getGuestPhone();
        String customerPhone = (rawPhone != null && rawPhone.matches("\\d{10,11}"))
                ? rawPhone : "0900000000";
        String customerAddress = event.getShippingAddress() != null ? event.getShippingAddress() : "Địa chỉ không xác định";

        // GHN yêu cầu mã district/ward chính xác; dùng district/ward của shop làm fallback demo
        Integer toDistrictId = ghnProperties.getFromDistrictId();
        String toWardCode = ghnProperties.getFromWardCode();
        if (toDistrictId == null || toWardCode == null) {
            log.warn("GHN fromDistrictId/fromWardCode chưa cấu hình — dùng giá trị mặc định cho orderId={}",
                    event.getOrderId());
            toDistrictId = toDistrictId != null ? toDistrictId : 3695;
            toWardCode = toWardCode != null ? toWardCode : "90768";
        }

        boolean isCod = "COD".equalsIgnoreCase(event.getPaymentMethod());
        // GHN sandbox caps both COD amount and insurance value at 50,000,000 VND
        BigDecimal GHN_MAX_VALUE = new BigDecimal("50000000");
        BigDecimal rawTotal = event.getTotalAmount() != null ? event.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal codAmount = isCod ? rawTotal.min(GHN_MAX_VALUE) : BigDecimal.ZERO;
        BigDecimal insuranceValue = rawTotal.min(GHN_MAX_VALUE);

        ShipmentItemRequest defaultItem = ShipmentItemRequest.builder()
                .name("NexaTech order #" + event.getOrderNumber())
                .quantity(1)
                .price(insuranceValue)
                .weight(500)
                .length(20)
                .width(15)
                .height(10)
                .build();

        CreateShipmentRequest shipmentRequest = CreateShipmentRequest.builder()
                .orderId(event.getOrderId().toString())
                .customerName(customerName)
                .customerPhone(customerPhone)
                .customerAddress(customerAddress)
                .toDistrictId(toDistrictId)
                .toWardCode(toWardCode)
                .weight(500)
                .length(20)
                .width(15)
                .height(10)
                .codAmount(codAmount)
                .insuranceValue(insuranceValue)
                .items(List.of(defaultItem))
                .build();

        log.info("Tạo shipment cho orderId={} customer={} phone={}", event.getOrderId(), customerName, customerPhone);
        try {
            shippingService.createShipment(shipmentRequest);
        } catch (Exception ex) {
            log.error("Tạo shipment thất bại cho orderId={}: {} ({})", event.getOrderId(), ex.getMessage(), ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }
}
