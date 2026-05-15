package com.nexatech.shipping.provider.ghn;

import com.nexatech.shipping.dto.*;
import com.nexatech.shipping.entity.Shipment;
import com.nexatech.shipping.entity.ShipmentTrackingHistory;
import com.nexatech.shipping.enums.ShipmentStatus;
import com.nexatech.shipping.enums.ShippingProvider;
import com.nexatech.shipping.exception.ShippingProviderException;
import com.nexatech.shipping.mapper.GhnShippingMapper;
import com.nexatech.shipping.mapper.ShipmentMapper;
import com.nexatech.shipping.provider.ShippingProviderClient;
import com.nexatech.shipping.provider.ghn.dto.*;
import com.nexatech.shipping.repository.ShipmentRepository;
import com.nexatech.shipping.repository.ShipmentTrackingHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    @Override
    public ShippingFeeResponse calculateFee(ShippingFeeRequest request) {
        ensureConfigured();
        GhnFeeRequest ghnRequest = ghnShippingMapper.toFeeRequest(request, properties);
        GhnFeeResponse ghnResponse = post("/v2/shipping-order/fee", ghnRequest, GhnFeeResponse.class);
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
    @Transactional
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
        addHistory(saved, ShipmentStatus.PENDING_PICKUP, properties.getFromAddress(), null, null, "GHN shipment created");
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
                shipmentRepository.save(shipment);
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
    public CancelShipmentResponse cancelShipment(String trackingCode) {
        throw new UnsupportedOperationException("GHN cancel shipment is not implemented yet");
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
                                    .map(responseBody -> new ShippingProviderException(
                                            "GHN API error " + response.statusCode().value() + ": " + responseBody)))
                    .bodyToMono(responseType)
                    .block(Duration.ofSeconds(15));
        } catch (ShippingProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ShippingProviderException("Failed to call GHN API: " + ex.getMessage(), ex);
        }
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
        Integer fee = data.getTotalFee() != null ? data.getTotalFee() : data.getFee();
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
}
