package com.nexatech.shipping.service;

import com.nexatech.shipping.dto.*;
import com.nexatech.shipping.entity.Shipment;
import com.nexatech.shipping.enums.ShipmentStatus;
import com.nexatech.shipping.enums.ShippingProvider;
import com.nexatech.shipping.exception.ResourceNotFoundException;
import com.nexatech.shipping.mapper.ShipmentMapper;
import com.nexatech.shipping.provider.ghn.GhnShippingClient;
import com.nexatech.shipping.provider.mock.MockShippingClient;
import com.nexatech.shipping.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShippingProviderFactory providerFactory;
    private final MockShippingClient mockShippingClient;
    private final GhnShippingClient ghnShippingClient;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentMapper shipmentMapper;
    private final AuditLogService auditLogService;

    public ShippingFeeResponse calculateFee(ShippingFeeRequest request) {
        return providerFactory.getCurrentProviderClient().calculateFee(request);
    }

    public CreateShipmentResponse createShipment(CreateShipmentRequest request) {
        return providerFactory.getCurrentProviderClient().createShipment(request);
    }

    public TrackingResponse getTracking(String trackingCode) {
        return shipmentRepository.findByTrackingCode(trackingCode)
                .map(shipment -> providerFactory.getProviderClient(shipment.getProvider()).getTracking(trackingCode))
                .orElseGet(() -> providerFactory.getCurrentProviderClient().getTracking(trackingCode));
    }

    public CancelShipmentResponse cancelShipment(String trackingCode) {
        return shipmentRepository.findByTrackingCode(trackingCode)
                .map(shipment -> providerFactory.getProviderClient(shipment.getProvider()).cancelShipment(trackingCode))
                .orElseGet(() -> providerFactory.getCurrentProviderClient().cancelShipment(trackingCode));
    }

    public Page<TrackingResponse> getShipments(ShipmentStatus status, Pageable pageable) {
        Page<Shipment> shipments = status == null
                ? shipmentRepository.findAll(pageable)
                : shipmentRepository.findByStatus(status, pageable);
        return shipments.map(shipmentMapper::toTrackingSummaryResponse);
    }

    public TrackingResponse getShipmentByOrderId(String orderId) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found with orderId: " + orderId));
        return shipmentMapper.toTrackingResponse(shipment);
    }

    public TrackingResponse updateMockStatus(String trackingCode, MockUpdateStatusRequest request) {
        return mockShippingClient.updateStatus(trackingCode, request);
    }

    public void cancelShipmentByOrderIdIfPresent(String orderId, String actor) {
        shipmentRepository.findByOrderId(orderId).ifPresentOrElse(shipment -> {
            if (isTerminal(shipment.getStatus())) {
                log.info("Skip cancelling terminal shipment: orderId={} trackingCode={} status={}",
                        orderId, shipment.getTrackingCode(), shipment.getStatus());
                return;
            }

            if (shipment.getProvider() == ShippingProvider.MOCK) {
                mockShippingClient.cancelShipment(shipment.getTrackingCode());
            } else if (shipment.getProvider() == ShippingProvider.GHN) {
                ghnShippingClient.cancelShipment(shipment.getTrackingCode());
            }
        }, () -> auditLogService.log("ORDER_CANCELLED_NO_SHIPMENT", "Shipment", orderId,
                "No shipment found for cancelled order", actor));
    }

    private boolean isTerminal(ShipmentStatus status) {
        return status == ShipmentStatus.DELIVERED
                || status == ShipmentStatus.FAILED
                || status == ShipmentStatus.RETURNED
                || status == ShipmentStatus.CANCELLED;
    }
}
