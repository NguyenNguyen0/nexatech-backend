package com.nexatech.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.payment.model.OutboxEvent;
import com.nexatech.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lưu domain event vào outbox_events trong cùng transaction nghiệp vụ.
 * Không publish Kafka trực tiếp — OutboxPublisherScheduler sẽ xử lý async.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Serialize payload thành JSON và lưu vào outbox.
     * Phải được gọi trong cùng @Transactional với nghiệp vụ chính.
     */
    @Transactional
    public void saveEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(json)
                    .build();
            outboxEventRepository.save(event);
            log.debug("Outbox event saved: type={} aggregateId={}", eventType, aggregateId);
        } catch (Exception e) {
            log.error("Không thể lưu outbox event: type={} aggregateId={} error={}", eventType, aggregateId, e.getMessage());
            throw new RuntimeException("Outbox save failed — rolling back transaction", e);
        }
    }
}
