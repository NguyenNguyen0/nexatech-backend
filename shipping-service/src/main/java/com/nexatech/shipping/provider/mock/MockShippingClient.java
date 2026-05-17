package com.nexatech.shipping.provider.mock;

import com.nexatech.shipping.dto.*;
import com.nexatech.shipping.entity.Shipment;
import com.nexatech.shipping.entity.ShipmentTrackingHistory;
import com.nexatech.shipping.enums.ShipmentStatus;
import com.nexatech.shipping.enums.ShippingProvider;
import com.nexatech.shipping.event.ShippingEvent;
import com.nexatech.shipping.exception.ResourceNotFoundException;
import com.nexatech.shipping.mapper.ShipmentMapper;
import com.nexatech.shipping.provider.ShippingProviderClient;
import com.nexatech.shipping.repository.ShipmentRepository;
import com.nexatech.shipping.repository.ShipmentTrackingHistoryRepository;
import com.nexatech.shipping.service.AuditLogService;
import com.nexatech.shipping.service.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MockShippingClient implements ShippingProviderClient {

    private static final BigDecimal BASE_FEE = BigDecimal.valueOf(15_000);
    private static final BigDecimal WEIGHT_STEP_FEE = BigDecimal.valueOf(5_000);
    private static final String CURRENCY = "VND";
    private static final String WAREHOUSE_ADDRESS = "NexaTech Warehouse, Thu Duc, Ho Chi Minh City";

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingHistoryRepository historyRepository;
    private final ShipmentMapper shipmentMapper;
    private final OutboxEventService outboxEventService;
    private final AuditLogService auditLogService;

    @Override
    public ShippingFeeResponse calculateFee(ShippingFeeRequest request) {
        return ShippingFeeResponse.builder()
                .provider(ShippingProvider.MOCK)
                .fee(calculateMockFee(request.getWeight()))
                .currency(CURRENCY)
                .estimatedDeliveryTime(LocalDateTime.now().plusDays(3))
                .build();
    }

    @Override
    @Transactional
    public CreateShipmentResponse createShipment(CreateShipmentRequest request) {
        BigDecimal fee = calculateMockFee(request.getWeight());
        String trackingCode = "NX-SHIP-" + System.currentTimeMillis();

        Shipment shipment = Shipment.builder()
                .orderId(request.getOrderId())
                .provider(ShippingProvider.MOCK)
                .trackingCode(trackingCode)
                .status(ShipmentStatus.PENDING_PICKUP)
                .shippingFee(fee)
                .currency(CURRENCY)
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .customerAddress(request.getCustomerAddress())
                .customerProvince(request.getCustomerProvince())
                .customerDistrict(request.getCustomerDistrict())
                .customerWard(request.getCustomerWard())
                .toDistrictId(request.getToDistrictId())
                .toWardCode(request.getToWardCode())
                .weight(request.getWeight())
                .length(request.getLength())
                .width(request.getWidth())
                .height(request.getHeight())
                .codAmount(defaultMoney(request.getCodAmount()))
                .currentAddress(WAREHOUSE_ADDRESS)
                .build();

        Shipment saved = shipmentRepository.save(shipment);
        addHistory(saved, ShipmentStatus.PENDING_PICKUP, WAREHOUSE_ADDRESS, null, null, "Shipment created in mock provider");
        saveShippingEventToOutbox(saved, "SHIPMENT_CREATED");
        auditLogService.log("SHIPMENT_CREATED", "Shipment", saved.getId().toString(),
                "status=PENDING_PICKUP", "mock-provider");
        return shipmentMapper.toCreateResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TrackingResponse getTracking(String trackingCode) {
        Shipment shipment = findByTrackingCode(trackingCode);
        return shipmentMapper.toTrackingResponse(shipment);
    }

    @Override
    @Transactional
    public CancelShipmentResponse cancelShipment(String trackingCode) {
        Shipment shipment = findByTrackingCode(trackingCode);
        shipment.setStatus(ShipmentStatus.CANCELLED);
        addHistory(shipment, ShipmentStatus.CANCELLED, shipment.getCurrentAddress(), shipment.getCurrentLat(), shipment.getCurrentLng(), "Shipment cancelled");
        Shipment saved = shipmentRepository.save(shipment);
        saveShippingEventToOutbox(saved, "SHIPMENT_CANCELLED");
        auditLogService.log("SHIPMENT_CANCELLED", "Shipment", saved.getId().toString(),
                "status=CANCELLED", "mock-provider");

        return CancelShipmentResponse.builder()
                .trackingCode(trackingCode)
                .status(ShipmentStatus.CANCELLED)
                .message("Mock shipment cancelled successfully")
                .build();
    }

    @Transactional
    public TrackingResponse updateStatus(String trackingCode, MockUpdateStatusRequest request) {
        Shipment shipment = findByTrackingCode(trackingCode);
        if (shipment.getProvider() != ShippingProvider.MOCK) {
            throw new IllegalArgumentException("Only MOCK shipments can be updated by this endpoint");
        }

        shipment.setStatus(request.getStatus());
        shipment.setCurrentLat(request.getLat());
        shipment.setCurrentLng(request.getLng());
        shipment.setCurrentAddress(request.getLocationAddress());
        addHistory(shipment, request.getStatus(), request.getLocationAddress(), request.getLat(), request.getLng(), request.getNote());
        Shipment saved = shipmentRepository.save(shipment);
        saveShippingEventToOutbox(saved, "SHIPMENT_STATUS_UPDATED");
        auditLogService.log("SHIPMENT_STATUS_UPDATED", "Shipment", saved.getId().toString(),
                null, "status=" + saved.getStatus(), "mock-provider", request.getNote());
        return shipmentMapper.toTrackingResponse(saved);
    }

    private Shipment findByTrackingCode(String trackingCode) {
        return shipmentRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found with trackingCode: " + trackingCode));
    }

    private BigDecimal calculateMockFee(Integer weightInGram) {
        int billableKilograms = (int) Math.ceil(weightInGram / 1000.0);
        return BASE_FEE.add(WEIGHT_STEP_FEE.multiply(BigDecimal.valueOf(billableKilograms)));
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void addHistory(Shipment shipment, ShipmentStatus status, String address, Double lat, Double lng, String note) {
        historyRepository.save(ShipmentTrackingHistory.builder()
                .shipment(shipment)
                .status(status)
                .locationAddress(address)
                .lat(lat)
                .lng(lng)
                .note(note)
                .eventTime(LocalDateTime.now())
                .build());
    }

    private void saveShippingEventToOutbox(Shipment shipment, String eventType) {
        ShippingEvent event = ShippingEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .timestamp(Instant.now())
                .shipmentId(shipment.getId())
                .orderId(shipment.getOrderId())
                .provider(shipment.getProvider())
                .trackingCode(shipment.getTrackingCode())
                .status(shipment.getStatus())
                .shippingFee(shipment.getShippingFee())
                .currency(shipment.getCurrency())
                .build();
        outboxEventService.saveEvent("Shipment", shipment.getId().toString(), eventType, event);
    }
}
