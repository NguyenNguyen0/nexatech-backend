package com.nexatech.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request tạo yêu cầu trả hàng từ phía khách hàng.
 */
@Data
public class CreateReturnRequest {

    /** Lý do trả hàng tổng thể. */
    @NotBlank
    private String reason;

    /** Danh sách sản phẩm cần trả (bắt buộc phải có ít nhất 1 sản phẩm). */
    @NotEmpty
    private List<ReturnItemRequest> items;

    @Data
    public static class ReturnItemRequest {

        @NotNull
        private String productId;

        private String productName;

        private int quantity;

        /** Lý do trả riêng cho sản phẩm (tuỳ chọn). */
        private String reason;
    }
}
