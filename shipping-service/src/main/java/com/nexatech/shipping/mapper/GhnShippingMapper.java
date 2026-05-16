package com.nexatech.shipping.mapper;

import com.nexatech.shipping.dto.CreateShipmentRequest;
import com.nexatech.shipping.dto.ShipmentItemRequest;
import com.nexatech.shipping.dto.ShippingFeeRequest;
import com.nexatech.shipping.provider.ghn.GhnProperties;
import com.nexatech.shipping.provider.ghn.dto.GhnCreateOrderRequest;
import com.nexatech.shipping.provider.ghn.dto.GhnFeeRequest;
import com.nexatech.shipping.provider.ghn.dto.GhnItemRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class GhnShippingMapper {

    public GhnFeeRequest toFeeRequest(ShippingFeeRequest request, GhnProperties properties) {
        return GhnFeeRequest.builder()
                .serviceId(request.getServiceId())
                .serviceTypeId(resolveServiceTypeId(request.getServiceTypeId()))
                .insuranceValue(toInteger(request.getInsuranceValue()))
                .fromDistrictId(properties.getFromDistrictId())
                .fromWardCode(properties.getFromWardCode())
                .toDistrictId(request.getToDistrictId())
                .toWardCode(request.getToWardCode())
                .height(request.getHeight())
                .length(request.getLength())
                .weight(request.getWeight())
                .width(request.getWidth())
                .items(List.of(defaultFeeItem(request)))
                .build();
    }

    public GhnCreateOrderRequest toCreateOrderRequest(CreateShipmentRequest request, GhnProperties properties) {
        return GhnCreateOrderRequest.builder()
                .paymentTypeId(2)
                .note("NexaTech order")
                .requiredNote("KHONGCHOXEMHANG")
                .toName(request.getCustomerName())
                .toPhone(request.getCustomerPhone())
                .toAddress(request.getCustomerAddress())
                .toWardCode(request.getToWardCode())
                .toDistrictId(request.getToDistrictId())
                .codAmount(toInteger(request.getCodAmount()))
                .insuranceValue(toInteger(request.getInsuranceValue()))
                .weight(request.getWeight())
                .length(request.getLength())
                .width(request.getWidth())
                .height(request.getHeight())
                .serviceId(request.getServiceId())
                .serviceTypeId(resolveServiceTypeId(request.getServiceTypeId()))
                .items(toGhnItems(request.getItems()))
                .build();
    }

    private List<GhnItemRequest> toGhnItems(List<ShipmentItemRequest> items) {
        return items.stream()
                .map(item -> GhnItemRequest.builder()
                        .name(item.getName())
                        .quantity(item.getQuantity())
                        .price(toInteger(item.getPrice()))
                        .weight(item.getWeight())
                        .length(item.getLength())
                        .width(item.getWidth())
                        .height(item.getHeight())
                        .build())
                .toList();
    }

    private GhnItemRequest defaultFeeItem(ShippingFeeRequest request) {
        return GhnItemRequest.builder()
                .name("NexaTech order item")
                .quantity(1)
                .price(toInteger(request.getInsuranceValue()))
                .weight(request.getWeight())
                .length(request.getLength())
                .width(request.getWidth())
                .height(request.getHeight())
                .build();
    }

    private Integer resolveServiceTypeId(Integer serviceTypeId) {
        return serviceTypeId == null ? 2 : serviceTypeId;
    }

    private Integer toInteger(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        return value.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
