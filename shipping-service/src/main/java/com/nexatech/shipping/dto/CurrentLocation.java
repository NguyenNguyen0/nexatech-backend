package com.nexatech.shipping.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentLocation {

    private Double lat;
    private Double lng;
    private String address;
}
