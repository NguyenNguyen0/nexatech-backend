package com.nexatech.shipping.mapper;

import com.nexatech.shipping.dto.*;
import com.nexatech.shipping.entity.Shipment;
import com.nexatech.shipping.entity.ShipmentTrackingHistory;
import com.nexatech.shipping.repository.ShipmentTrackingHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ShipmentMapper {

    private final ShipmentTrackingHistoryRepository historyRepository;

    public CreateShipmentResponse toCreateResponse(Shipment shipment) {
        return CreateShipmentResponse.builder()
                .shipmentId(shipment.getId())
                .provider(shipment.getProvider())
                .trackingCode(shipment.getTrackingCode())
                .status(shipment.getStatus())
                .shippingFee(shipment.getShippingFee())
                .currency(shipment.getCurrency())
                .build();
    }

    public TrackingResponse toTrackingResponse(Shipment shipment) {
        List<TrackingHistoryResponse> histories = historyRepository
                .findByShipmentIdOrderByEventTimeAsc(shipment.getId())
                .stream()
                .map(this::toTrackingHistoryResponse)
                .toList();

        return TrackingResponse.builder()
                .orderId(shipment.getOrderId())
                .trackingCode(shipment.getTrackingCode())
                .provider(shipment.getProvider())
                .status(shipment.getStatus())
                .shippingFee(shipment.getShippingFee())
                .currency(shipment.getCurrency())
                .customerName(shipment.getCustomerName())
                .customerPhone(shipment.getCustomerPhone())
                .customerAddress(shipment.getCustomerAddress())
                .createdAt(shipment.getCreatedAt())
                .updatedAt(shipment.getUpdatedAt())
                .currentLocation(CurrentLocation.builder()
                        .lat(shipment.getCurrentLat())
                        .lng(shipment.getCurrentLng())
                        .address(shipment.getCurrentAddress())
                        .build())
                .histories(histories)
                .build();
    }

    public TrackingResponse toTrackingSummaryResponse(Shipment shipment) {
        return TrackingResponse.builder()
                .orderId(shipment.getOrderId())
                .trackingCode(shipment.getTrackingCode())
                .provider(shipment.getProvider())
                .status(shipment.getStatus())
                .shippingFee(shipment.getShippingFee())
                .currency(shipment.getCurrency())
                .customerName(shipment.getCustomerName())
                .customerPhone(shipment.getCustomerPhone())
                .customerAddress(shipment.getCustomerAddress())
                .createdAt(shipment.getCreatedAt())
                .updatedAt(shipment.getUpdatedAt())
                .currentLocation(CurrentLocation.builder()
                        .lat(shipment.getCurrentLat())
                        .lng(shipment.getCurrentLng())
                        .address(shipment.getCurrentAddress())
                        .build())
                .histories(List.of())
                .build();
    }

    public TrackingHistoryResponse toTrackingHistoryResponse(ShipmentTrackingHistory history) {
        return TrackingHistoryResponse.builder()
                .status(history.getStatus())
                .locationAddress(history.getLocationAddress())
                .lat(history.getLat())
                .lng(history.getLng())
                .note(history.getNote())
                .eventTime(history.getEventTime())
                .build();
    }
}
