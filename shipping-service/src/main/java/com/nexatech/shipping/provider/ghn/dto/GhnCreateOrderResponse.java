package com.nexatech.shipping.provider.ghn.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GhnCreateOrderResponse {

    private Integer code;
    private String message;
    private CreateOrderData data;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderData {
        @JsonProperty("order_code")
        private String orderCode;

        @JsonProperty("total_fee")
        private Integer totalFee;

        @JsonAlias({"fee", "main_service"})
        private Integer fee;
    }
}
