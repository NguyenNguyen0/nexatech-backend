package com.nexatech.shipping.repository;

import com.nexatech.shipping.entity.ShipmentIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentIdempotencyKeyRepository extends JpaRepository<ShipmentIdempotencyKey, UUID> {

    Optional<ShipmentIdempotencyKey> findByIdempotencyKey(String idempotencyKey);
}
