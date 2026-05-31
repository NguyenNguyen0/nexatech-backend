package com.nexatech.inventory.repository;

import com.nexatech.inventory.model.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    Optional<InventoryItem> findByProductId(String productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId = :productId")
    Optional<InventoryItem> findLockedByProductId(@Param("productId") String productId);

    Optional<InventoryItem> findBySku(String sku);

    List<InventoryItem> findByProductIdIn(List<String> productIds);

    List<InventoryItem> findByQuantityLessThanEqual(int threshold);

    Page<InventoryItem> findByWarehouseLocation(String warehouseLocation, Pageable pageable);
}
