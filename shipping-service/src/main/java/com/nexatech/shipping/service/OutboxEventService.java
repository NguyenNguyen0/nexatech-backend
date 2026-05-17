package com.nexatech.shipping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.shipping.entity.OutboxEvent;
import com.nexatech.shipping.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

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
            log.debug("Shipping outbox event saved: type={} aggregateId={}", eventType, aggregateId);
        } catch (Exception e) {
            log.error("Unable to save shipping outbox event: type={} aggregateId={} error={}",
                    eventType, aggregateId, e.getMessage());
            throw new RuntimeException("Shipping outbox save failed", e);
        }
    }
}
