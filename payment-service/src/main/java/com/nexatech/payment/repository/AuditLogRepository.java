package com.nexatech.payment.repository;

import com.nexatech.payment.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** Toàn bộ audit log của một entity, theo thứ tự thời gian — dùng để replay state. */
    List<AuditLog> findByEntityIdOrderByCreatedAtAsc(String entityId);

    /** Audit log trong khoảng thời gian — dùng cho point-in-time query sau snapshot. */
    List<AuditLog> findByEntityIdAndCreatedAtAfterAndCreatedAtBeforeOrderByCreatedAtAsc(
            String entityId, Instant after, Instant before);

    /** Tra cứu toàn bộ luồng theo correlationId (traceId) — xuyên service. */
    List<AuditLog> findByCorrelationIdOrderByCreatedAtAsc(String correlationId);
}
