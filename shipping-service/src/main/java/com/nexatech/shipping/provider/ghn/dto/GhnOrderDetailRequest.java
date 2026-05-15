package com.nexatech.shipping.provider.ghn.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GhnOrderDetailRequest {

    @JsonProperty("order_code")
    private String orderCode;
}
