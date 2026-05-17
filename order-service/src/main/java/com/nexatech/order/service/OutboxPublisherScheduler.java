package com.nexatech.order.service;

import com.nexatech.order.enums.OutboxStatus;
import com.nexatech.order.model.OutboxEvent;
import com.nexatech.order.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler đọc outbox_events PENDING và publish lên Kafka.
 * Chạy mỗi 5 giây, xử lý tối đa 50 event mỗi lần.
 * Nếu service restart, các event PENDING được retry tự động.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherScheduler {

    private static final int MAX_RETRY = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublisher  outboxEventPublisher;

    @Scheduled(fixedDelay = 5_000)
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository
                .findTop50ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(OutboxStatus.PENDING, MAX_RETRY);

        if (pending.isEmpty()) return;

        log.debug("OutboxScheduler: xử lý {} event(s)", pending.size());

        for (OutboxEvent event : pending) {
            // Mỗi event trong transaction REQUIRES_NEW độc lập
            outboxEventPublisher.publishAndMark(event);
        }
    }
}
