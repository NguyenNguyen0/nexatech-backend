package com.nexatech.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.order.enums.OutboxStatus;
import com.nexatech.order.model.OutboxEvent;
import com.nexatech.order.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Lưu event vào bảng outbox_events.
 * Phải được gọi trong cùng @Transactional với nghiệp vụ chính (createOrder, cancelOrder, ...).
 * Không publish Kafka trực tiếp — OutboxPublisherScheduler xử lý bất đồng bộ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Serialize payload thành JSON và lưu vào outbox.
     *
     * @param aggregateType loại aggregate, ví dụ "Order"
     * @param aggregateId   ID của aggregate (dùng làm Kafka message key)
     * @param eventType     tên event, ví dụ "ORDER_PLACED"
     * @param payload       object event — sẽ được serialize thành JSON
     */
    public void saveEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .status(OutboxStatus.PENDING)
                    .build();
            outboxEventRepository.save(outboxEvent);
            log.debug("Đã lưu outbox event: type={}, aggregateId={}", eventType, aggregateId);
        } catch (Exception e) {
            log.error("Không thể lưu outbox event type={}, aggregateId={}: {}", eventType, aggregateId, e.getMessage());
            // Ném lại để transaction nghiệp vụ rollback — đảm bảo tính nhất quán
            throw new RuntimeException("Không thể lưu outbox event: " + eventType, e);
        }
    }
}
