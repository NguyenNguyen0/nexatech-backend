package com.nexatech.shipping.provider.ghn.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GhnFeeResponse {

    private Integer code;
    private String message;
    private FeeData data;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeeData {
        private Integer total;

        @JsonProperty("service_fee")
        private Integer serviceFee;

        @JsonProperty("insurance_fee")
        private Integer insuranceFee;
    }
}
