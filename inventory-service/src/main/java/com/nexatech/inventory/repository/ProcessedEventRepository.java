package com.nexatech.inventory.repository;

import com.nexatech.inventory.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);

    boolean existsByEventTypeAndConsumerNameAndAggregateId(String eventType, String consumerName, String aggregateId);
}
