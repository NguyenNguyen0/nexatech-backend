package com.nexatech.shipping.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingFeeRequest {

    private String orderId;

    @NotNull
    private Integer toDistrictId;

    @NotBlank
    private String toWardCode;

    private String customerProvince;
    private String customerDistrict;
    private String customerWard;
    private String customerAddress;

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

    private BigDecimal insuranceValue;
    private Integer serviceId;
    private Integer serviceTypeId;
}
