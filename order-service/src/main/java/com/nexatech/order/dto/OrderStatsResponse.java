package com.nexatech.order.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Response cho admin dashboard thống kê đơn hàng.
 */
@Data
@Builder
public class OrderStatsResponse {

    private String from;
    private String to;

    private long totalOrders;
    private BigDecimal totalRevenue;
    private BigDecimal averageOrderValue;

    // Phân bổ đơn hàng theo trạng thái: {"PENDING": 10, "COMPLETED": 5, ...}
    private Map<String, Long> ordersByStatus;

    // Doanh thu theo ngày trong khoảng thời gian
    private List<DailyRevenue> revenueByDay;

    // Top sản phẩm bán chạy
    private List<TopProduct> topProducts;

    @Data
    @Builder
    public static class DailyRevenue {
        private String date;         // "2026-03-22"
        private BigDecimal revenue;
        private long orderCount;
    }

    @Data
    @Builder
    public static class TopProduct {
        private String productId;
        private String productName;
        private long totalQuantity;
        private BigDecimal totalRevenue;
    }
}
