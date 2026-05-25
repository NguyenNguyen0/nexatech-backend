package com.nexatech.inventory.dto;

import com.nexatech.inventory.model.StockMovement;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class StockMovementResponse {

    private UUID id;
    private String productId;
    private StockMovement.MovementType type;
    private int quantity;
    private String reference;
    private String reason;
    private UUID performedBy;
    private Instant createdAt;
}