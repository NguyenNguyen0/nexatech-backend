package com.nexatech.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingPaymentTimeoutScheduler {

    private static final String ACTOR = "order-service:PendingPaymentTimeoutScheduler";

    private final OrderService orderService;

    @Value("${order.payment.pending-timeout-minutes:20}")
    private long pendingTimeoutMinutes;

    @Scheduled(fixedDelayString = "${order.payment.pending-timeout-scan-delay-ms:60000}")
    public void cancelExpiredPendingPayments() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(pendingTimeoutMinutes));
        try {
            orderService.cancelExpiredPendingOrders(cutoff, ACTOR);
        } catch (Exception e) {
            log.error("Unable to cancel expired pending payment orders: {}", e.getMessage(), e);
        }
    }
}
