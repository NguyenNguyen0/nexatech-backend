package com.nexatech.shipping.dto;

import com.nexatech.shipping.enums.ShipmentStatus;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelShipmentResponse {

    private String trackingCode;
    private ShipmentStatus status;
    private String message;
}
