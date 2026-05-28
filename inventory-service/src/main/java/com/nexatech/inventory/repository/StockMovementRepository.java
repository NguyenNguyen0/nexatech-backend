package com.nexatech.inventory.repository;

import com.nexatech.inventory.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findByProductId(String productId, Pageable pageable);

    boolean existsByProductIdAndTypeAndReference(String productId, StockMovement.MovementType type, String reference);
}
