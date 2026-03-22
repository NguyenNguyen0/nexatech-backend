package com.nexatech.order.model;

import com.nexatech.order.entity.base.BaseEntity;
import com.nexatech.order.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    private BigDecimal shippingFee;

    private BigDecimal discount;

    private String shippingAddress;

    private String note;

    // Phương thức vận chuyển (STANDARD, EXPRESS) — khách chọn khi đặt hàng
    private String shippingMethod;

    // Thông tin theo dõi vận chuyển — admin cập nhật sau khi gửi hàng
    private String trackingNumber;
    private String carrierName;     // Tên đơn vị vận chuyển (GHN, GHTK, VNPost, ...)
    private Instant estimatedDelivery;
    private Instant shippedAt;      // Tự động gán khi status → SHIPPED
    private Instant deliveredAt;    // Tự động gán khi status → DELIVERED

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();
}
