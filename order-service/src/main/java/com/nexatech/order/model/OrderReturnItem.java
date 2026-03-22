package com.nexatech.order.model;

import com.nexatech.order.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Entity đại diện cho từng sản phẩm trong yêu cầu trả hàng.
 */
@Entity
@Table(name = "order_return_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReturnItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_return_id", nullable = false)
    private OrderReturn orderReturn;

    @Column(nullable = false)
    private String productId;

    private String productName;

    @Column(nullable = false)
    private int quantity;

    /** Lý do trả riêng cho sản phẩm này (tuỳ chọn). */
    private String itemReason;
}
