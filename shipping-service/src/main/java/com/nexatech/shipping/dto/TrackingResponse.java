package com.nexatech.shipping.dto;

import com.nexatech.shipping.enums.ShipmentStatus;
import com.nexatech.shipping.enums.ShippingProvider;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingResponse {

    private String orderId;
    private String trackingCode;
    private ShippingProvider provider;
    private ShipmentStatus status;
    private BigDecimal shippingFee;
    private String currency;
    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private CurrentLocation currentLocation;
    private List<TrackingHistoryResponse> histories;
}
