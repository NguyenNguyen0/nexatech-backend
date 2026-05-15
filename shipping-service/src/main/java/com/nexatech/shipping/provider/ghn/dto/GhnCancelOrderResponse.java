package com.nexatech.shipping.provider.ghn.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GhnCancelOrderResponse {

    private Integer code;
    private String message;
    private List<CancelResult> data;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CancelResult {

        @JsonProperty("order_code")
        private String orderCode;

        private Boolean result;

        private String message;
    }
}
