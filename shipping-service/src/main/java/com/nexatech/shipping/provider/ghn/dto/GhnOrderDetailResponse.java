package com.nexatech.shipping.provider.ghn.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GhnOrderDetailResponse {

    private Integer code;
    private String message;
    private OrderDetailData data;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderDetailData {
        @JsonProperty("order_code")
        private String orderCode;

        private String status;

        @JsonProperty("to_address")
        private String toAddress;

        @JsonProperty("current_warehouse_name")
        private String currentWarehouseName;

        @JsonProperty("return_name")
        private String returnName;
    }
}
