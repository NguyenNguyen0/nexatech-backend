package com.nexatech.inventory.repository;

import com.nexatech.inventory.model.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    Optional<InventoryItem> findByProductId(String productId);

    List<InventoryItem> findByProductIdIn(List<String> productIds);

    List<InventoryItem> findByQuantityLessThanEqual(int threshold);
}
