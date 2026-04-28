package com.nexatech.payment.repository;

import com.nexatech.payment.enums.PaymentMethod;
import com.nexatech.payment.enums.PaymentStatus;
import com.nexatech.payment.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByTransactionId(String transactionId);

    Page<Payment> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Payment> findByCustomerIdAndStatus(UUID customerId, PaymentStatus status, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    long countByCreatedAtBetween(Instant from, Instant to);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status AND p.createdAt BETWEEN :from AND :to")
    BigDecimal sumAmountByStatusAndPeriod(@Param("status") PaymentStatus status, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT p.status, COUNT(p) FROM Payment p WHERE p.createdAt BETWEEN :from AND :to GROUP BY p.status")
    List<Object[]> countByStatusBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT p.method, COUNT(p) FROM Payment p WHERE p.createdAt BETWEEN :from AND :to GROUP BY p.method")
    List<Object[]> countByMethodBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
            SELECT DATE(created_at) AS day, COUNT(*) AS cnt, COALESCE(SUM(amount), 0) AS rev
            FROM payments
            WHERE status = 'COMPLETED' AND created_at BETWEEN :from AND :to
            GROUP BY DATE(created_at)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> revenueByDay(@Param("from") Instant from, @Param("to") Instant to);
}

