package com.nexatech.order.repository;

import com.nexatech.order.model.OrderIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderIdempotencyKeyRepository extends JpaRepository<OrderIdempotencyKey, UUID> {

    Optional<OrderIdempotencyKey> findByIdempotencyKey(String idempotencyKey);
}
