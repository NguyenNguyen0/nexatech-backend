package com.nexatech.shipping.dto;

import com.nexatech.shipping.enums.ShipmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockUpdateStatusRequest {

    @NotNull
    private ShipmentStatus status;

    private Double lat;
    private Double lng;
    private String locationAddress;
    private String note;
}
