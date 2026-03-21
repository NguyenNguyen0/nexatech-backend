package com.nexatech.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO nội bộ của order-service — đại diện thông tin sản phẩm nhận từ product-service qua Feign.
 * Chỉ lấy những trường cần thiết để tính giá và kiểm tra trạng thái.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductInfo {

    private String id;
    private String name;
    private BigDecimal price;
    private boolean active;
}
