package com.nexatech.shipping.repository;

import com.nexatech.shipping.entity.OutboxEvent;
import com.nexatech.shipping.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop50ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(OutboxStatus status, int maxRetry);
}
