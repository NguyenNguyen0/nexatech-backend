package com.nexatech.shipping.dto;

import com.nexatech.shipping.enums.ShipmentStatus;
import com.nexatech.shipping.enums.ShippingProvider;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShipmentResponse {

    private UUID shipmentId;
    private ShippingProvider provider;
    private String trackingCode;
    private ShipmentStatus status;
    private BigDecimal shippingFee;
    private String currency;
}
