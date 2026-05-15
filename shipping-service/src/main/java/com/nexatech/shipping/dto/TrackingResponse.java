package com.nexatech.shipping.dto;

import com.nexatech.shipping.enums.ShipmentStatus;
import com.nexatech.shipping.enums.ShippingProvider;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingResponse {

    private String trackingCode;
    private ShippingProvider provider;
    private ShipmentStatus status;
    private CurrentLocation currentLocation;
    private List<TrackingHistoryResponse> histories;
}
