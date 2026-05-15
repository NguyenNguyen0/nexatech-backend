package com.nexatech.shipping.provider.ghn.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GhnCancelOrderRequest {

    @JsonProperty("order_codes")
    private List<String> orderCodes;
}
