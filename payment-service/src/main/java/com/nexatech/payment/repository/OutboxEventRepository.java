package com.nexatech.payment.repository;

import com.nexatech.payment.enums.OutboxStatus;
import com.nexatech.payment.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop50ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(OutboxStatus status, int maxRetry);
}
