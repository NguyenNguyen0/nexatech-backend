package com.nexatech.shipping.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexatech.shipping.enums.ShippingProvider;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShippingFeeResponse {

    private ShippingProvider provider;
    private BigDecimal fee;
    private String currency;
    private LocalDateTime estimatedDeliveryTime;
    private Object rawProviderResponse;
}
