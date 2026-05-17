package com.nexatech.shipping.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShipmentRequest {

    @NotBlank
    private String orderId;

    @NotBlank
    private String customerName;

    @NotBlank
    private String customerPhone;

    @NotBlank
    private String customerAddress;

    private String customerProvince;
    private String customerDistrict;
    private String customerWard;

    @NotNull
    private Integer toDistrictId;

    @NotBlank
    private String toWardCode;

    @NotNull
    @Min(1)
    private Integer weight;

    @NotNull
    @Min(1)
    private Integer length;

    @NotNull
    @Min(1)
    private Integer width;

    @NotNull
    @Min(1)
    private Integer height;

    private BigDecimal codAmount;
    private BigDecimal insuranceValue;
    private Integer serviceId;
    private Integer serviceTypeId;

    @Valid
    @NotEmpty
    private List<ShipmentItemRequest> items;
}
