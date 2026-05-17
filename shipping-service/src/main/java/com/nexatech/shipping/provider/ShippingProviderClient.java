package com.nexatech.shipping.provider;

import com.nexatech.shipping.dto.*;

public interface ShippingProviderClient {

    ShippingFeeResponse calculateFee(ShippingFeeRequest request);

    CreateShipmentResponse createShipment(CreateShipmentRequest request);

    TrackingResponse getTracking(String trackingCode);

    CancelShipmentResponse cancelShipment(String trackingCode);
}
