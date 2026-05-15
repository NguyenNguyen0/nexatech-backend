package com.nexatech.shipping.provider.ghn.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GhnItemRequest {

    private String name;
    private Integer quantity;
    private Integer price;
    private Integer weight;
    private Integer length;
    private Integer width;
    private Integer height;

    @JsonProperty("item_code")
    private String itemCode;
}
