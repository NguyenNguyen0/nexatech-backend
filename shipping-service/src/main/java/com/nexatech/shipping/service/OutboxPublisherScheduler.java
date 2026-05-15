package com.nexatech.shipping.service;

import com.nexatech.shipping.entity.OutboxEvent;
import com.nexatech.shipping.enums.OutboxStatus;
import com.nexatech.shipping.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

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

        for (OutboxEvent event : events) {
            outboxEventPublisher.publishAndMark(event);
        }
    }
}
