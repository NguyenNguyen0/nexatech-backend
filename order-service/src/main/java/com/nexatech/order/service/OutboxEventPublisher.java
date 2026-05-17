package com.nexatech.order.service;

import com.nexatech.order.enums.OutboxStatus;
import com.nexatech.order.model.OutboxEvent;
import com.nexatech.order.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Thực thi publish một outbox event lên Kafka và cập nhật trạng thái DB.
 * Tách thành bean riêng để @Transactional(REQUIRES_NEW) hoạt động đúng qua Spring AOP proxy.
 * Được gọi từ OutboxPublisherScheduler.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private static final int MAX_RETRY  = 5;
    private static final int KAFKA_TIMEOUT_SECONDS = 5;

    private static final String ORDER_EVENTS_TOPIC = "order.events";

    private final OutboxEventRepository outboxEventRepository;

    @Qualifier("stringKafkaTemplate")
    private final KafkaTemplate<String, String> stringKafkaTemplate;

    /**
     * Publish một event lên Kafka rồi cập nhật trạng thái trong transaction mới.
     * REQUIRES_NEW đảm bảo mỗi event được commit độc lập — một event fail không ảnh hưởng event khác.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishAndMark(OutboxEvent event) {
        try {
            stringKafkaTemplate
                    .send(ORDER_EVENTS_TOPIC, event.getAggregateId(), event.getPayload())
                    .get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            event.setErrorMessage(null);
            outboxEventRepository.save(event);
            log.info("Outbox: published id={} type={}", event.getId(), event.getEventType());

        } catch (Exception e) {
            int newCount = event.getRetryCount() + 1;
            event.setRetryCount(newCount);
            event.setErrorMessage(truncate(e.getMessage(), 500));

            if (newCount >= MAX_RETRY) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("Outbox: FAILED id={} type={} sau {} lần retry: {}",
                        event.getId(), event.getEventType(), MAX_RETRY, e.getMessage());
            } else {
                log.warn("Outbox: lần retry {}/{} id={} type={}: {}",
                        newCount, MAX_RETRY, event.getId(), event.getEventType(), e.getMessage());
            }

            outboxEventRepository.save(event);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
