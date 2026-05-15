package com.nexatech.shipping.dto;

import com.nexatech.shipping.enums.ShipmentStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingHistoryResponse {

    private ShipmentStatus status;
    private String locationAddress;
    private Double lat;
    private Double lng;
    private String note;
    private LocalDateTime eventTime;
}
