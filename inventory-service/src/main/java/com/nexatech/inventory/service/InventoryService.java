package com.nexatech.inventory.service;

import com.nexatech.inventory.dto.BulkImportResult;
import com.nexatech.inventory.dto.CSVImportError;
import com.nexatech.inventory.dto.CSVImportResult;
import com.nexatech.inventory.dto.InventoryResponse;
import com.nexatech.inventory.dto.StockCheckRequest;
import com.nexatech.inventory.dto.StockUpdateRequest;
import com.nexatech.inventory.dto.WarehouseUpdateRequest;
import com.nexatech.inventory.event.InventoryEvent;
import com.nexatech.inventory.event.InventoryEventProducer;
import com.nexatech.inventory.exception.BadRequestException;
import com.nexatech.inventory.exception.ResourceNotFoundException;
import com.nexatech.inventory.model.InventoryItem;
import com.nexatech.inventory.model.StockMovement;
import com.nexatech.inventory.repository.InventoryItemRepository;
import com.nexatech.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
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

    public BulkImportResult bulkImport(List<StockUpdateRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BadRequestException("Import list must not be empty");
        }

        int successCount = 0;
        int failureCount = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            StockUpdateRequest req = requests.get(i);
            try {
                updateStock(req);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                errors.add("Item[" + i + "] productId=" + req.getProductId() + ": " + e.getMessage());
            }
        }

        return BulkImportResult.builder()
                .successCount(successCount)
                .failureCount(failureCount)
                .errors(errors)
                .build();
    }

    public Page<InventoryResponse> getAllInventory(String warehouseLocation, Pageable pageable) {
        if (StringUtils.hasText(warehouseLocation)) {
            return inventoryItemRepository.findByWarehouseLocation(warehouseLocation, pageable)
                    .map(this::toResponse);
        }
        return inventoryItemRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public InventoryResponse updateWarehouseInfo(String productId, WarehouseUpdateRequest request) {
        InventoryItem item = inventoryItemRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", "productId", productId));

        item.setWarehouseLocation(request.getWarehouseLocation());
        item.setReorderLevel(request.getReorderLevel());

        InventoryItem saved = inventoryItemRepository.save(item);
        publishLowStockEventIfNeeded(saved);
        return toResponse(saved);
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

    @Transactional
    public CSVImportResult importFromCSV(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("CSV file must not be empty");
        }

        List<CSVImportError> errorLines = new ArrayList<>();
        int successCount = 0;
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean isHeaderLine = true;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Skip header
                if (isHeaderLine) {
                    isHeaderLine = false;
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length < 3) {
                    errorLines.add(CSVImportError.builder()
                            .lineNumber(lineNumber)
                            .sku(parts.length > 0 ? parts[0].trim() : "")
                            .reason("Invalid CSV format: expected at least 3 columns (sku, quantity, warehouse)")
                            .build());
                    continue;
                }

                String sku = parts[0].trim();
                String quantityStr = parts[1].trim();
                String warehouse = parts[2].trim();

                // Validate sku
                if (!StringUtils.hasText(sku)) {
                    errorLines.add(CSVImportError.builder()
                            .lineNumber(lineNumber)
                            .sku("")
                            .reason("SKU is required")
                            .build());
                    continue;
                }

                // Validate and parse quantity
                int quantity;
                try {
                    quantity = Integer.parseInt(quantityStr);
                    if (quantity < 0) {
                        throw new NumberFormatException("Negative quantity");
                    }
                } catch (NumberFormatException e) {
                    errorLines.add(CSVImportError.builder()
                            .lineNumber(lineNumber)
                            .sku(sku)
                            .reason("Invalid quantity: " + quantityStr)
                            .build());
                    continue;
                }

                try {
                    // Find by SKU
                    InventoryItem item = inventoryItemRepository.findBySku(sku)
                            .orElseThrow(() -> new ResourceNotFoundException("Inventory item", "sku", sku));

                    // Update stock
                    int oldQuantity = item.getQuantity();
                    item.setQuantity(quantity);
                    if (StringUtils.hasText(warehouse)) {
                        item.setWarehouseLocation(warehouse);
                    }
                    InventoryItem saved = inventoryItemRepository.save(item);

                    int delta = quantity - oldQuantity;
                    if (delta != 0) {
                        recordMovement(saved.getProductId(), StockMovement.MovementType.ADJUSTMENT, Math.abs(delta),
                                "CSV_IMPORT", "Imported from CSV line " + lineNumber, null);
                    }

                    publishLowStockEventIfNeeded(saved);
                    successCount++;

                } catch (ResourceNotFoundException e) {
                    errorLines.add(CSVImportError.builder()
                            .lineNumber(lineNumber)
                            .sku(sku)
                            .reason("SKU not found in inventory")
                            .build());
                } catch (Exception e) {
                    errorLines.add(CSVImportError.builder()
                            .lineNumber(lineNumber)
                            .sku(sku)
                            .reason("Failed to process: " + e.getMessage())
                            .build());
                }
            }

        } catch (Exception e) {
            throw new BadRequestException("Failed to parse CSV file: " + e.getMessage());
        }

        return CSVImportResult.builder()
                .successCount(successCount)
                .failureCount(errorLines.size())
                .totalLines(lineNumber - 1) // Exclude header
                .errorLines(errorLines)
                .build();
    }
}
