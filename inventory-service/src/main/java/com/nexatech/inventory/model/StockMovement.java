package com.nexatech.inventory.model;

import com.nexatech.inventory.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement extends BaseEntity {

    @Column(nullable = false)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    @Column(nullable = false)
    private int quantity;

    private String reference;

    private String reason;

    private UUID performedBy;

    public enum MovementType {
        INBOUND,
        OUTBOUND,
        RESERVED,
        RELEASED,
        ADJUSTMENT,
        SALE,
        RETURN
    }
}
