package com.nexatech.shipping.service;

import com.nexatech.shipping.entity.OutboxEvent;
import com.nexatech.shipping.enums.OutboxStatus;
import com.nexatech.shipping.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private static final int MAX_RETRY = 5;
    private static final String TOPIC = "shipping.events";

    private final OutboxEventRepository outboxEventRepository;

    @Qualifier("stringKafkaTemplate")
    private final KafkaTemplate<String, String> stringKafkaTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishAndMark(OutboxEvent event) {
        try {
            stringKafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            log.info("Shipping outbox published: id={} type={}", event.getId(), event.getEventType());
        } catch (Exception e) {
            int retries = event.getRetryCount() + 1;
            event.setRetryCount(retries);
            event.setErrorMessage(e.getMessage());
            if (retries >= MAX_RETRY) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("Shipping outbox failed after {} retries: id={} type={} error={}",
                        MAX_RETRY, event.getId(), event.getEventType(), e.getMessage());
            } else {
                log.warn("Shipping outbox retry {}/{}: id={} type={} error={}",
                        retries, MAX_RETRY, event.getId(), event.getEventType(), e.getMessage());
            }
        }
        outboxEventRepository.save(event);
    }
}
