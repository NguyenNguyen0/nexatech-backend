package com.nexatech.payment.repository;

import com.nexatech.payment.model.PaymentIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIdempotencyKeyRepository extends JpaRepository<PaymentIdempotencyKey, UUID> {

    Optional<PaymentIdempotencyKey> findByIdempotencyKey(String idempotencyKey);
}
