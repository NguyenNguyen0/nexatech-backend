package com.nexatech.order.event;

import com.nexatech.order.enums.ReturnStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Kafka event cho luồng trả hàng.
 * Được publish lên topic "order.events" để payment-service lắng nghe và xử lý hoàn tiền.
 *
 * Khi eventType = "ORDER_RETURN_APPROVED":
 *   payment-service tìm payment theo orderId → đổi trạng thái sang REFUNDED.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReturnEvent extends BaseEvent {

    private UUID returnId;

    /** payment-service cần orderId để tra cứu giao dịch tương ứng. */
    private UUID orderId;

    private UUID customerId;

    /** Trạng thái hiện tại của yêu cầu trả hàng. */
    private ReturnStatus status;

    private String reason;

    /** Tổng tiền cần hoàn = totalAmount của đơn hàng (payment-service quyết định). */
    private BigDecimal refundAmount;
}
