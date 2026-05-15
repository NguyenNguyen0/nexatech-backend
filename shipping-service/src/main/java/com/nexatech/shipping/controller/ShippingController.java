package com.nexatech.shipping.controller;

import com.nexatech.shipping.dto.*;
import com.nexatech.shipping.service.ShippingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;

    @PostMapping("/fee")
    public ApiResponse<ShippingFeeResponse> calculateFee(@Valid @RequestBody ShippingFeeRequest request) {
        return ApiResponse.ok("Shipping fee calculated successfully", shippingService.calculateFee(request));
    }

    @PostMapping("/shipments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateShipmentResponse> createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        return ApiResponse.ok("Shipment created successfully", shippingService.createShipment(request));
    }

    @GetMapping("/shipments/{trackingCode}/tracking")
    public ApiResponse<TrackingResponse> getTracking(@PathVariable String trackingCode) {
        return ApiResponse.ok("Shipment tracking fetched successfully", shippingService.getTracking(trackingCode));
    }

    @PostMapping("/shipments/{trackingCode}/cancel")
    public ApiResponse<CancelShipmentResponse> cancelShipment(@PathVariable String trackingCode) {
        return ApiResponse.ok("Shipment cancel request processed", shippingService.cancelShipment(trackingCode));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<TrackingResponse> getShipmentByOrderId(@PathVariable String orderId) {
        return ApiResponse.ok("Shipment fetched successfully", shippingService.getShipmentByOrderId(orderId));
    }

    @PatchMapping("/mock/{trackingCode}/status")
    public ApiResponse<TrackingResponse> updateMockStatus(
            @PathVariable String trackingCode,
            @Valid @RequestBody MockUpdateStatusRequest request) {
        return ApiResponse.ok("Mock shipment status updated successfully", shippingService.updateMockStatus(trackingCode, request));
    }
}
