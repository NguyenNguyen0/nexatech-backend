package com.nexatech.shipping.provider.ghn;

import com.nexatech.shipping.dto.*;
import com.nexatech.shipping.entity.Shipment;
import com.nexatech.shipping.entity.ShipmentTrackingHistory;
import com.nexatech.shipping.enums.ShipmentStatus;
import com.nexatech.shipping.enums.ShippingProvider;
import com.nexatech.shipping.event.ShippingEvent;
import com.nexatech.shipping.exception.ShippingProviderException;
import com.nexatech.shipping.mapper.GhnShippingMapper;
import com.nexatech.shipping.mapper.ShipmentMapper;
import com.nexatech.shipping.provider.ShippingProviderClient;
import com.nexatech.shipping.provider.ghn.dto.*;
import com.nexatech.shipping.provider.mock.MockShippingClient;
import com.nexatech.shipping.repository.ShipmentRepository;
import com.nexatech.shipping.repository.ShipmentTrackingHistoryRepository;
import com.nexatech.shipping.service.AuditLogService;
import com.nexatech.shipping.service.OutboxEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GhnShippingClient implements ShippingProviderClient {

    private static final String CURRENCY = "VND";

    private final WebClient ghnWebClient;
    private final GhnProperties properties;
    private final GhnShippingMapper ghnShippingMapper;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingHistoryRepository historyRepository;
    private final ShipmentMapper shipmentMapper;
    private final OutboxEventService outboxEventService;
    private final AuditLogService auditLogService;
    private final MockShippingClient mockShippingClient;

    @Override
    public ShippingFeeResponse calculateFee(ShippingFeeRequest request) {
        ensureConfigured();
        GhnFeeRequest ghnRequest = ghnShippingMapper.toFeeRequest(request, properties);
        GhnFeeResponse ghnResponse;
        try {
            ghnResponse = post("/v2/shipping-order/fee", ghnRequest, GhnFeeResponse.class);
        } catch (ShippingProviderException ex) {
            if (isTransientProviderError(ex)) {
                log.warn("GHN fee failed with transient error, falling back to mock fee: {}", ex.getMessage());
                ShippingFeeResponse fallback = mockShippingClient.calculateFee(request);
                fallback.setRawProviderResponse(Map.of(
                        "fallback", true,
                        "reason", ex.getMessage(),
                        "sourceProvider", ShippingProvider.GHN.name()
                ));
                return fallback;
            }
            throw ex;
        }
        Integer total = Optional.ofNullable(ghnResponse)
                .map(GhnFeeResponse::getData)
                .map(GhnFeeResponse.FeeData::getTotal)
                .orElseThrow(() -> new ShippingProviderException("GHN fee response is missing data.total"));

        return ShippingFeeResponse.builder()
                .provider(ShippingProvider.GHN)
                .fee(BigDecimal.valueOf(total))
                .currency(CURRENCY)
                .estimatedDeliveryTime(LocalDateTime.now().plusDays(3))
                .rawProviderResponse(ghnResponse)
                .build();
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public CreateShipmentResponse createShipment(CreateShipmentRequest request) {
        ensureConfigured();
        GhnCreateOrderRequest ghnRequest = ghnShippingMapper.toCreateOrderRequest(request, properties);
        GhnCreateOrderResponse ghnResponse = post("/v2/shipping-order/create", ghnRequest, GhnCreateOrderResponse.class);
        GhnCreateOrderResponse.CreateOrderData data = Optional.ofNullable(ghnResponse)
                .map(GhnCreateOrderResponse::getData)
                .orElseThrow(() -> new ShippingProviderException("GHN create order response is missing data"));

        if (!StringUtils.hasText(data.getOrderCode())) {
            throw new ShippingProviderException("GHN create order response is missing order_code");
        }

        Shipment shipment = Shipment.builder()
                .orderId(request.getOrderId())
                .provider(ShippingProvider.GHN)
                .trackingCode(data.getOrderCode())
                .status(ShipmentStatus.PENDING_PICKUP)
                .shippingFee(resolveCreateFee(data))
                .currency(CURRENCY)
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .customerAddress(request.getCustomerAddress())
                .customerProvince(request.getCustomerProvince())
                .customerDistrict(request.getCustomerDistrict())
                .customerWard(request.getCustomerWard())
                .toDistrictId(request.getToDistrictId())
                .toWardCode(request.getToWardCode())
                .weight(request.getWeight())
                .length(request.getLength())
                .width(request.getWidth())
                .height(request.getHeight())
                .codAmount(defaultMoney(request.getCodAmount()))
                .currentAddress(properties.getFromAddress())
                .build();

        Shipment saved = shipmentRepository.save(shipment);
        addHistory(saved, ShipmentStatus.PENDING_PICKUP, properties.getFromAddress(), null, null, "Đơn vận chuyển đã được tạo");
        saveShippingEventToOutbox(saved, "SHIPMENT_CREATED");
        auditLogService.log("SHIPMENT_CREATED", "Shipment", saved.getId().toString(),
                "status=PENDING_PICKUP", "ghn-provider");
        return shipmentMapper.toCreateResponse(saved);
    }

    @Override
    @Transactional
    public TrackingResponse getTracking(String trackingCode) {
        ensureConfigured();
        GhnOrderDetailResponse ghnResponse = post(
                "/v2/shipping-order/detail",
                GhnOrderDetailRequest.builder().orderCode(trackingCode).build(),
                GhnOrderDetailResponse.class
        );

        GhnOrderDetailResponse.OrderDetailData data = Optional.ofNullable(ghnResponse)
                .map(GhnOrderDetailResponse::getData)
                .orElseThrow(() -> new ShippingProviderException("GHN order detail response is missing data"));

        ShipmentStatus mappedStatus = mapGhnStatus(data.getStatus());
        Optional<Shipment> shipmentOptional = shipmentRepository.findByTrackingCode(trackingCode);
        if (shipmentOptional.isPresent()) {
            Shipment shipment = shipmentOptional.get();
            if (shipment.getStatus() != mappedStatus) {
                shipment.setStatus(mappedStatus);
                shipment.setCurrentAddress(resolveCurrentAddress(data, shipment.getCurrentAddress()));
                addHistory(shipment, mappedStatus, shipment.getCurrentAddress(), shipment.getCurrentLat(), shipment.getCurrentLng(),
                        "GHN status: " + data.getStatus());
                Shipment saved = shipmentRepository.save(shipment);
                saveShippingEventToOutbox(saved, "SHIPMENT_STATUS_UPDATED");
                auditLogService.log("SHIPMENT_STATUS_UPDATED", "Shipment", saved.getId().toString(),
                        null, "status=" + saved.getStatus(), "ghn-provider", "GHN status: " + data.getStatus());
            }
            return shipmentMapper.toTrackingResponse(shipment);
        }

        return TrackingResponse.builder()
                .trackingCode(trackingCode)
                .provider(ShippingProvider.GHN)
                .status(mappedStatus)
                .currentLocation(CurrentLocation.builder()
                        .address(resolveCurrentAddress(data, null))
                        .build())
                .histories(List.of())
                .build();
    }

    @Override
    @Transactional
    public CancelShipmentResponse cancelShipment(String trackingCode) {
        ensureConfigured();

        GhnCancelOrderRequest ghnRequest = GhnCancelOrderRequest.builder()
                .orderCodes(List.of(trackingCode))
                .build();

        GhnCancelOrderResponse ghnResponse = post(
                "/v2/switch-status/cancel",
                ghnRequest,
                GhnCancelOrderResponse.class
        );

        // Validate GHN response
        boolean cancelled = Optional.ofNullable(ghnResponse)
                .map(GhnCancelOrderResponse::getData)
                .filter(data -> !data.isEmpty())
                .map(data -> data.get(0))
                .map(result -> Boolean.TRUE.equals(result.getResult()))
                .orElse(false);

        if (!cancelled) {
            String reason = Optional.ofNullable(ghnResponse)
                    .map(GhnCancelOrderResponse::getData)
                    .filter(data -> !data.isEmpty())
                    .map(data -> data.get(0).getMessage())
                    .orElse("GHN did not confirm cancellation");
            throw new ShippingProviderException("GHN cancel failed for order " + trackingCode + ": " + reason);
        }

        // Update local state
        Shipment shipment = shipmentRepository.findByTrackingCode(trackingCode)
                .orElse(null);

        if (shipment != null) {
            ShipmentStatus previousStatus = shipment.getStatus();
            shipment.setStatus(ShipmentStatus.CANCELLED);
            addHistory(shipment, ShipmentStatus.CANCELLED, shipment.getCurrentAddress(), null, null, "Cancelled via GHN API");
            shipmentRepository.save(shipment);
            saveShippingEventToOutbox(shipment, "SHIPMENT_CANCELLED");
            auditLogService.log("SHIPMENT_CANCELLED", "Shipment", shipment.getId().toString(),
                    previousStatus.name(), "status=CANCELLED", "ghn-provider", "GHN cancel requested");
        }

        return CancelShipmentResponse.builder()
                .trackingCode(trackingCode)
                .status(ShipmentStatus.CANCELLED)
                .message("Shipment cancelled successfully via GHN")
                .build();
    }

    public void validateConfiguration() {
        ensureConfigured();
    }

    private <T> T post(String uri, Object body, Class<T> responseType) {
        try {
            return ghnWebClient.post()
                    .uri(uri)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(responseBody -> Mono.error(new ShippingProviderException(
                                            "GHN API error " + response.statusCode().value() + ": " + responseBody,
                                            response.statusCode().value()))))
                    .bodyToMono(responseType)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(300))
                            .filter(this::isTransientProviderError))
                    .block(Duration.ofSeconds(15));
        } catch (ShippingProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ShippingProviderException("Failed to call GHN API: " + ex.getMessage(), ex);
        }
    }

    private boolean isTransientProviderError(Throwable ex) {
        log.warn("GHN retry check: exceptionType={} message={}", ex.getClass().getSimpleName(), ex.getMessage());
        if (ex instanceof ShippingProviderException providerException) {
            Integer statusCode = providerException.getStatusCode();
            return statusCode == null || statusCode >= 500;
        }
        return true;
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(properties.getToken())) {
            throw new ShippingProviderException("GHN configuration is missing GHN_TOKEN");
        }
        if (!StringUtils.hasText(properties.getShopId())) {
            throw new ShippingProviderException("GHN configuration is missing GHN_SHOP_ID");
        }
        if (properties.getFromDistrictId() == null) {
            throw new ShippingProviderException("GHN configuration is missing GHN_FROM_DISTRICT_ID");
        }
        if (!StringUtils.hasText(properties.getFromWardCode())) {
            throw new ShippingProviderException("GHN configuration is missing GHN_FROM_WARD_CODE");
        }
    }

    private ShipmentStatus mapGhnStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return ShipmentStatus.IN_TRANSIT;
        }
        return switch (status.toLowerCase()) {
            case "ready_to_pick", "picking", "money_collect_picking" -> ShipmentStatus.PENDING_PICKUP;
            case "picked" -> ShipmentStatus.PICKED_UP;
            case "storing", "transporting", "sorting", "delivering" -> ShipmentStatus.IN_TRANSIT;
            case "delivered" -> ShipmentStatus.DELIVERED;
            case "delivery_fail" -> ShipmentStatus.FAILED;
            case "waiting_to_return", "return", "returned" -> ShipmentStatus.RETURNED;
            case "cancel", "cancelled" -> ShipmentStatus.CANCELLED;
            default -> ShipmentStatus.IN_TRANSIT;
        };
    }

    private BigDecimal resolveCreateFee(GhnCreateOrderResponse.CreateOrderData data) {
        Integer fee = data.getTotalFee();
        return fee == null ? BigDecimal.ZERO : BigDecimal.valueOf(fee);
    }

    private String resolveCurrentAddress(GhnOrderDetailResponse.OrderDetailData data, String fallback) {
        if (StringUtils.hasText(data.getCurrentWarehouseName())) {
            return data.getCurrentWarehouseName();
        }
        if (StringUtils.hasText(data.getToAddress())) {
            return data.getToAddress();
        }
        return fallback;
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void addHistory(Shipment shipment, ShipmentStatus status, String address, Double lat, Double lng, String note) {
        historyRepository.save(ShipmentTrackingHistory.builder()
                .shipment(shipment)
                .status(status)
                .locationAddress(address)
                .lat(lat)
                .lng(lng)
                .note(note)
                .eventTime(LocalDateTime.now())
                .build());
    }

    private void saveShippingEventToOutbox(Shipment shipment, String eventType) {
        ShippingEvent event = ShippingEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .timestamp(Instant.now())
                .shipmentId(shipment.getId())
                .orderId(shipment.getOrderId())
                .provider(shipment.getProvider())
                .trackingCode(shipment.getTrackingCode())
                .status(shipment.getStatus())
                .shippingFee(shipment.getShippingFee())
                .currency(shipment.getCurrency())
                .build();
        outboxEventService.saveEvent("Shipment", shipment.getId().toString(), eventType, event);
    }
}
