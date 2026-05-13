package com.nexatech.order.repository;

import com.nexatech.order.enums.OutboxStatus;
import com.nexatech.order.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Lấy tối đa 50 events PENDING có retry_count < maxRetry, sắp xếp theo thời gian tạo */
    List<OutboxEvent> findTop50ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
            OutboxStatus status, int maxRetry);
}
