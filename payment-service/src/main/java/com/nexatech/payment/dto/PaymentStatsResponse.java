package com.nexatech.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class PaymentStatsResponse {

    private LocalDate from;
    private LocalDate to;
    private long totalPayments;
    private BigDecimal totalRevenue;
    private Map<String, Long> byStatus;
    private Map<String, Long> byMethod;
    private List<DailyRevenue> dailyRevenue;

    @Data
    @Builder
    public static class DailyRevenue {
        private LocalDate date;
        private long count;
        private BigDecimal revenue;
    }
}
