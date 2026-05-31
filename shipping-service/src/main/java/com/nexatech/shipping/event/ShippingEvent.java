package com.nexatech.shipping.event;

import com.nexatech.shipping.enums.ShipmentStatus;
import com.nexatech.shipping.enums.ShippingProvider;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ShippingEvent extends BaseEvent {

    private UUID shipmentId;
    private String orderId;
    private ShippingProvider provider;
    private String trackingCode;
    private ShipmentStatus status;
    private BigDecimal shippingFee;
    private String currency;
    private String failureReason;
}
