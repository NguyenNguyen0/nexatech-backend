package com.nexatech.payment.service;

import com.nexatech.payment.dto.PaymentStatsResponse;
import com.nexatech.payment.dto.PaymentStatsResponse.DailyRevenue;
import com.nexatech.payment.enums.PaymentStatus;
import com.nexatech.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentStatsService {

    private final PaymentRepository paymentRepository;

    public PaymentStatsResponse getStats(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        long total = paymentRepository.countByCreatedAtBetween(start, end);
        BigDecimal revenue = paymentRepository.sumAmountByStatusAndPeriod(PaymentStatus.COMPLETED, start, end);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        paymentRepository.countByStatusBetween(start, end)
                .forEach(row -> byStatus.put(row[0].toString(), (Long) row[1]));

        Map<String, Long> byMethod = new LinkedHashMap<>();
        paymentRepository.countByMethodBetween(start, end)
                .forEach(row -> byMethod.put(row[0].toString(), (Long) row[1]));

        List<DailyRevenue> daily = paymentRepository.revenueByDay(start, end)
                .stream()
                .map(row -> DailyRevenue.builder()
                        .date(toLocalDate(row[0]))
                        .count(((Number) row[1]).longValue())
                        .revenue(toBigDecimal(row[2]))
                        .build())
                .toList();

        return PaymentStatsResponse.builder()
                .from(from)
                .to(to)
                .totalPayments(total)
                .totalRevenue(revenue)
                .byStatus(byStatus)
                .byMethod(byMethod)
                .dailyRevenue(daily)
                .build();
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }
}
