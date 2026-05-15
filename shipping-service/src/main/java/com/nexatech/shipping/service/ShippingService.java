package com.nexatech.shipping.service;

import com.nexatech.shipping.dto.*;
import com.nexatech.shipping.entity.Shipment;
import com.nexatech.shipping.exception.ResourceNotFoundException;
import com.nexatech.shipping.mapper.ShipmentMapper;
import com.nexatech.shipping.provider.mock.MockShippingClient;
import com.nexatech.shipping.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShippingProviderFactory providerFactory;
    private final MockShippingClient mockShippingClient;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentMapper shipmentMapper;

    public ShippingFeeResponse calculateFee(ShippingFeeRequest request) {
        return providerFactory.getCurrentProviderClient().calculateFee(request);
    }

    public CreateShipmentResponse createShipment(CreateShipmentRequest request) {
        return providerFactory.getCurrentProviderClient().createShipment(request);
    }

    public TrackingResponse getTracking(String trackingCode) {
        return providerFactory.getCurrentProviderClient().getTracking(trackingCode);
    }

    public CancelShipmentResponse cancelShipment(String trackingCode) {
        return providerFactory.getCurrentProviderClient().cancelShipment(trackingCode);
    }

    public TrackingResponse getShipmentByOrderId(String orderId) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found with orderId: " + orderId));
        return shipmentMapper.toTrackingResponse(shipment);
    }

    public TrackingResponse updateMockStatus(String trackingCode, MockUpdateStatusRequest request) {
        return mockShippingClient.updateStatus(trackingCode, request);
    }
}
