package com.nexatech.order.service;

import com.nexatech.order.dto.OrderStatsResponse;
import com.nexatech.order.enums.OrderStatus;
import com.nexatech.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderStatsService {

    private final OrderRepository orderRepository;

    /**
     * Tổng hợp thống kê đơn hàng trong khoảng ngày [from, to] (bao gồm cả 2 đầu).
     */
    public OrderStatsResponse getStats(LocalDate from, LocalDate to) {
        // Chuyển sang Instant theo UTC — toInstant của ngày kế tiếp để bao gồm cả ngày `to`
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long totalOrders = orderRepository.countByCreatedAtBetween(fromInstant, toInstant);

        BigDecimal totalRevenue = orderRepository.sumRevenueBetween(fromInstant, toInstant);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        // Tính giá trị trung bình đơn hàng
        BigDecimal avgOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Phân bổ theo trạng thái
        Map<String, Long> byStatus = buildStatusMap(
                orderRepository.countByStatusBetween(fromInstant, toInstant)
        );
        // Điền trạng thái còn thiếu vào map với giá trị 0
        for (OrderStatus s : OrderStatus.values()) {
            byStatus.putIfAbsent(s.name(), 0L);
        }

        // Doanh thu theo từng ngày
        List<OrderStatsResponse.DailyRevenue> dailyRevenue = orderRepository
                .revenueByDay(fromInstant, toInstant)
                .stream()
                .map(row -> OrderStatsResponse.DailyRevenue.builder()
                        .date(row[0] != null ? row[0].toString() : "")
                        .revenue(toBigDecimal(row[1]))
                        .orderCount(toLong(row[2]))
                        .build())
                .collect(Collectors.toList());

        // Top 10 sản phẩm bán chạy
        List<OrderStatsResponse.TopProduct> topProducts = orderRepository
                .topProducts(fromInstant, toInstant, 10)
                .stream()
                .map(row -> OrderStatsResponse.TopProduct.builder()
                        .productId(row[0] != null ? row[0].toString() : "")
                        .productName(row[1] != null ? row[1].toString() : "")
                        .totalQuantity(toLong(row[2]))
                        .totalRevenue(toBigDecimal(row[3]))
                        .build())
                .collect(Collectors.toList());

        return OrderStatsResponse.builder()
                .from(from.toString())
                .to(to.toString())
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderValue(avgOrderValue)
                .ordersByStatus(byStatus)
                .revenueByDay(dailyRevenue)
                .topProducts(topProducts)
                .build();
    }

    private Map<String, Long> buildStatusMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            String key = row[0] instanceof OrderStatus
                    ? ((OrderStatus) row[0]).name()
                    : String.valueOf(row[0]);
            map.put(key, toLong(row[1]));
        }
        return map;
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        return new BigDecimal(val.toString());
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }
}
