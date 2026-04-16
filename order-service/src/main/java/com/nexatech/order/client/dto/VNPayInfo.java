package com.nexatech.order.client.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
public class VNPayInfo {

    private UUID paymentId;
    private String paymentUrl;
}
