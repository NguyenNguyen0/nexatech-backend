package com.nexatech.order.model;

import com.nexatech.order.entity.base.BaseEntity;
import com.nexatech.order.enums.ReturnStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity đại diện cho một yêu cầu trả hàng của khách.
 * Quan hệ 1-1 với Order (mỗi đơn chỉ có một yêu cầu trả).
 */
@Entity
@Table(name = "order_returns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReturn extends BaseEntity {

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID customerId;

    /** Lý do trả hàng do khách điền. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnStatus status;

    /** Ghi chú của admin khi từ chối yêu cầu. */
    private String adminNote;

    @OneToMany(mappedBy = "orderReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderReturnItem> items = new ArrayList<>();
}
