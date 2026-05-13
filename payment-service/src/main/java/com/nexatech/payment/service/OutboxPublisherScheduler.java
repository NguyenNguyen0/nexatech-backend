package com.nexatech.payment.service;

import com.nexatech.payment.enums.OutboxStatus;
import com.nexatech.payment.model.OutboxEvent;
import com.nexatech.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Định kỳ đọc PENDING events từ outbox và publish lên Kafka.
 * Tách khỏi OutboxEventPublisher để bean riêng có thể dùng @Transactional(REQUIRES_NEW).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherScheduler {

    private static final int MAX_RETRY = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Scheduled(fixedDelay = 5_000)
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository
                .findTop50ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(OutboxStatus.PENDING, MAX_RETRY);

        if (!events.isEmpty()) {
            log.debug("Outbox scheduler: {} pending events to publish", events.size());
        }

        for (OutboxEvent event : events) {
            outboxEventPublisher.publishAndMark(event);
        }
    }
}
