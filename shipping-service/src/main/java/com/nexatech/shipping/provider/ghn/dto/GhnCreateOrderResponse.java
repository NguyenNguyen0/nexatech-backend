package com.nexatech.shipping.provider.ghn.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GhnCreateOrderResponse {

    private Integer code;
    private String message;
    private CreateOrderData data;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateOrderData {
        @JsonProperty("order_code")
        private String orderCode;

        @JsonProperty("total_fee")
        private Integer totalFee;
    }
}
