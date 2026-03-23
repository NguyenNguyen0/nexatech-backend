package com.nexatech.inventory.service;

import com.nexatech.inventory.dto.InventoryResponse;
import com.nexatech.inventory.dto.StockCheckRequest;
import com.nexatech.inventory.dto.StockUpdateRequest;
import com.nexatech.inventory.event.InventoryEvent;
import com.nexatech.inventory.event.InventoryEventProducer;
import com.nexatech.inventory.exception.BadRequestException;
import com.nexatech.inventory.exception.ResourceNotFoundException;
import com.nexatech.inventory.model.InventoryItem;
import com.nexatech.inventory.model.StockMovement;
import com.nexatech.inventory.repository.InventoryItemRepository;
import com.nexatech.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final int DEFAULT_REORDER_LEVEL = 10;

    private final InventoryItemRepository inventoryItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryEventProducer inventoryEventProducer;

    public InventoryResponse getStock(String productId) {
        InventoryItem item = inventoryItemRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", "productId", productId));
        return toResponse(item);
    }

    @Transactional
    public InventoryResponse updateStock(StockUpdateRequest request) {
        InventoryItem item = inventoryItemRepository.findByProductId(request.getProductId())
                .orElseGet(() -> InventoryItem.builder()
                        .productId(request.getProductId())
                        .quantity(0)
                        .reservedQuantity(0)
                        .reorderLevel(DEFAULT_REORDER_LEVEL)
                        .build());

        if (request.getQuantity() < item.getReservedQuantity()) {
            throw new BadRequestException("Quantity cannot be lower than reserved quantity");
        }

        int oldQuantity = item.getQuantity();
        item.setQuantity(request.getQuantity());
        InventoryItem saved = inventoryItemRepository.save(item);

        int delta = request.getQuantity() - oldQuantity;
        if (delta != 0) {
            recordMovement(saved.getProductId(), StockMovement.MovementType.ADJUSTMENT, Math.abs(delta),
                    "MANUAL_UPDATE", request.getReason(), null);
        }

        publishLowStockEventIfNeeded(saved);
        return toResponse(saved);
    }

    public boolean checkStock(StockCheckRequest request) {
        validateItems(request);

        for (StockCheckRequest.Item itemRequest : request.getItems()) {
            InventoryItem item = inventoryItemRepository.findByProductId(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory item", "productId", itemRequest.getProductId()));

            int available = item.getQuantity() - item.getReservedQuantity();
            if (available < itemRequest.getQuantity()) {
                return false;
            }
        }
        return true;
    }

    @Transactional
    public void reserveStock(StockCheckRequest request) {
        reserveStock(request, "API_RESERVE", "Stock reserved from API");
    }

    @Transactional
    public void releaseStock(StockCheckRequest request) {
        releaseStock(request, "API_RELEASE", "Stock released from API");
    }

    public List<InventoryResponse> getLowStockItems() {
        return inventoryItemRepository.findAll().stream()
                .filter(this::isLowStock)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void reserveStock(StockCheckRequest request, String reference, String reason) {
        validateItems(request);
        if (!checkStock(request)) {
            throw new BadRequestException("Insufficient stock for one or more products");
        }

        for (StockCheckRequest.Item itemRequest : request.getItems()) {
            InventoryItem item = inventoryItemRepository.findByProductId(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory item", "productId", itemRequest.getProductId()));

            item.setReservedQuantity(item.getReservedQuantity() + itemRequest.getQuantity());
            InventoryItem saved = inventoryItemRepository.save(item);

            recordMovement(saved.getProductId(), StockMovement.MovementType.RESERVED,
                    itemRequest.getQuantity(), reference, reason, null);
            publishLowStockEventIfNeeded(saved);
        }
    }

    @Transactional
    public void releaseStock(StockCheckRequest request, String reference, String reason) {
        validateItems(request);

        for (StockCheckRequest.Item itemRequest : request.getItems()) {
            InventoryItem item = inventoryItemRepository.findByProductId(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory item", "productId", itemRequest.getProductId()));

            if (item.getReservedQuantity() < itemRequest.getQuantity()) {
                throw new BadRequestException("Release quantity exceeds reserved quantity for product: "
                        + itemRequest.getProductId());
            }

            item.setReservedQuantity(item.getReservedQuantity() - itemRequest.getQuantity());
            inventoryItemRepository.save(item);

            recordMovement(item.getProductId(), StockMovement.MovementType.RELEASED,
                    itemRequest.getQuantity(), reference, reason, null);
        }
    }

    @Transactional
    public void finalizeSale(StockCheckRequest request, String reference) {
        validateItems(request);

        for (StockCheckRequest.Item itemRequest : request.getItems()) {
            InventoryItem item = inventoryItemRepository.findByProductId(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory item", "productId", itemRequest.getProductId()));

            if (item.getReservedQuantity() < itemRequest.getQuantity()) {
                throw new BadRequestException("Not enough reserved stock for product: " + itemRequest.getProductId());
            }
            if (item.getQuantity() < itemRequest.getQuantity()) {
                throw new BadRequestException("Insufficient quantity for product: " + itemRequest.getProductId());
            }

            item.setReservedQuantity(item.getReservedQuantity() - itemRequest.getQuantity());
            item.setQuantity(item.getQuantity() - itemRequest.getQuantity());
            InventoryItem saved = inventoryItemRepository.save(item);

            recordMovement(saved.getProductId(), StockMovement.MovementType.SALE,
                    itemRequest.getQuantity(), reference, "Finalized sale from order event", null);
            publishLowStockEventIfNeeded(saved);
        }
    }

    private void validateItems(StockCheckRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Items list must not be empty");
        }
    }

    private InventoryResponse toResponse(InventoryItem item) {
        return InventoryResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .reservedQuantity(item.getReservedQuantity())
                .availableQuantity(item.getQuantity() - item.getReservedQuantity())
                .reorderLevel(item.getReorderLevel())
                .warehouseLocation(item.getWarehouseLocation())
                .build();
    }

    private void recordMovement(String productId,
                                StockMovement.MovementType type,
                                int quantity,
                                String reference,
                                String reason,
                                UUID performedBy) {
        stockMovementRepository.save(StockMovement.builder()
                .productId(productId)
                .type(type)
                .quantity(quantity)
                .reference(reference)
                .reason(reason)
                .performedBy(performedBy)
                .build());
    }

    private boolean isLowStock(InventoryItem item) {
        return item.getQuantity() - item.getReservedQuantity() <= item.getReorderLevel();
    }

    private void publishLowStockEventIfNeeded(InventoryItem item) {
        if (!isLowStock(item)) {
            return;
        }

        InventoryEvent event = InventoryEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("LOW_STOCK")
                .timestamp(Instant.now())
                .productId(item.getProductId())
                .productName(null)
                .quantity(item.getQuantity() - item.getReservedQuantity())
                .reorderLevel(item.getReorderLevel())
                .warehouseLocation(item.getWarehouseLocation())
                .build();

        inventoryEventProducer.publishInventoryEvent(item.getProductId(), event);
    }
}
