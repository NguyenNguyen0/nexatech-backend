package com.nexatech.order.dto;

import com.nexatech.order.enums.ReturnStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response trả về thông tin yêu cầu trả hàng.
 */
@Data
@Builder
public class ReturnResponse {

    private UUID id;
    private UUID orderId;
    private UUID customerId;
    private String reason;
    private ReturnStatus status;
    private String adminNote;
    private List<ReturnItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    public static class ReturnItemResponse {
        private UUID id;
        private String productId;
        private String productName;
        private int quantity;
        private String itemReason;
    }
}
