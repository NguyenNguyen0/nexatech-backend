package com.nexatech.inventory.service;

import com.nexatech.inventory.client.ProductClient;
import com.nexatech.inventory.client.dto.ProductSummary;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final int DEFAULT_REORDER_LEVEL = 10;
    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of(
            "productId", "productName", "sku", "quantity", "reservedQuantity", "availableQuantity",
            "reorderLevel", "status", "warehouseLocation");

    private final InventoryItemRepository inventoryItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryEventProducer inventoryEventProducer;
    private final ProductClient productClient;

    public InventoryResponse getStock(String productId) {
        InventoryItem item = inventoryItemRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", "productId", productId));
        return toResponse(item);
    }

    @Transactional
    public InventoryResponse updateStock(StockUpdateRequest request) {
        ProductSummary productSummary = fetchProductSummary(request.getProductId()).orElse(null);
        InventoryItem item = inventoryItemRepository.findByProductId(request.getProductId())
                .orElseGet(() -> InventoryItem.builder()
                        .productId(request.getProductId())
                        .sku(resolveSku(request, null, productSummary))
                        .quantity(0)
                        .reservedQuantity(0)
                        .reorderLevel(DEFAULT_REORDER_LEVEL)
                        .build());

        item.setSku(resolveSku(request, item.getSku(), productSummary));

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

    public Page<InventoryResponse> getAllInventory(String sku,
                                                   String status,
                                                   String keyword,
                                                   String warehouseLocation,
                                                   Pageable pageable) {
        Pageable safePageable = sanitizePageable(pageable);
        List<InventoryResponse> filtered = inventoryItemRepository.findAll().stream()
                .filter(item -> matchesWarehouse(item, warehouseLocation))
                .filter(item -> matchesSku(item, sku))
                .map(this::toResponse)
                .filter(response -> matchesStatus(response, status))
                .filter(response -> matchesKeyword(response, keyword))
                .sorted(comparatorFor(safePageable.getSort()))
                .toList();

        int from = (int) safePageable.getOffset();
        if (from >= filtered.size()) {
            return new PageImpl<>(List.of(), safePageable, filtered.size());
        }
        int to = Math.min(from + safePageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(from, to), safePageable, filtered.size());
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
        int availableQuantity = item.getQuantity() - item.getReservedQuantity();
        InventoryResponse.InventoryResponseBuilder builder = InventoryResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
            .sku(item.getSku())
                .quantity(item.getQuantity())
                .reservedQuantity(item.getReservedQuantity())
            .availableQuantity(availableQuantity)
                .reorderLevel(item.getReorderLevel())
                .warehouseLocation(item.getWarehouseLocation())
            .status(resolveStatus(availableQuantity, item.getReorderLevel()));

        fetchProductSummary(item.getProductId()).ifPresent(product -> builder
            .productName(product.getName())
            .productSlug(product.getSlug())
            .productThumbnail(product.getThumbnail()));

        return builder.build();
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

    private Optional<ProductSummary> fetchProductSummary(String productId) {
        try {
            return Optional.ofNullable(productClient.getProduct(productId))
                    .map(response -> response.getData());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String resolveSku(StockUpdateRequest request, String existingSku, ProductSummary productSummary) {
        if (StringUtils.hasText(request.getSku())) {
            return request.getSku().trim();
        }
        if (StringUtils.hasText(existingSku)) {
            return existingSku;
        }
        if (productSummary != null && StringUtils.hasText(productSummary.getSku())) {
            return productSummary.getSku().trim();
        }
        throw new BadRequestException("SKU is required when creating inventory for a product");
    }

    private String resolveStatus(int availableQuantity, int reorderLevel) {
        if (availableQuantity <= 0) {
            return "OUT_OF_STOCK";
        }
        if (availableQuantity <= reorderLevel) {
            return "LOW_STOCK";
        }
        return "IN_STOCK";
    }

    private boolean matchesWarehouse(InventoryItem item, String warehouseLocation) {
        if (!StringUtils.hasText(warehouseLocation)) {
            return true;
        }
        return warehouseLocation.trim().equalsIgnoreCase(item.getWarehouseLocation());
    }

    private boolean matchesSku(InventoryItem item, String sku) {
        if (!StringUtils.hasText(sku)) {
            return true;
        }
        return StringUtils.hasText(item.getSku())
                && item.getSku().toLowerCase(Locale.ROOT).contains(sku.trim().toLowerCase(Locale.ROOT));
    }

    private boolean matchesStatus(InventoryResponse response, String status) {
        if (!StringUtils.hasText(status)) {
            return true;
        }
        return status.trim().equalsIgnoreCase(response.getStatus());
    }

    private boolean matchesKeyword(InventoryResponse response, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }

        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(response.getSku(), normalizedKeyword)
                || containsIgnoreCase(response.getProductName(), normalizedKeyword)
                || containsIgnoreCase(response.getProductId(), normalizedKeyword)
                || containsIgnoreCase(response.getWarehouseLocation(), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int page = pageable == null ? 0 : pageable.getPageNumber();
        int size = pageable == null ? 20 : pageable.getPageSize();
        Sort safeSort = Sort.by(Sort.Direction.ASC, "productId");
        if (pageable != null && pageable.getSort().isSorted()) {
            List<Sort.Order> accepted = pageable.getSort().stream()
                    .filter(order -> ALLOWED_SORT_FIELDS.contains(order.getProperty()))
                    .map(order -> new Sort.Order(order.getDirection(), order.getProperty()))
                    .toList();
            if (!accepted.isEmpty()) {
                safeSort = Sort.by(accepted);
            }
        }
        return org.springframework.data.domain.PageRequest.of(Math.max(page, 0), Math.max(size, 1), safeSort);
    }

    private Comparator<InventoryResponse> comparatorFor(Sort sort) {
        Comparator<InventoryResponse> comparator = null;
        for (Sort.Order order : sort) {
            Comparator<InventoryResponse> next = comparatorFor(order);
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        return comparator == null ? Comparator.comparing(InventoryResponse::getProductId, Comparator.nullsLast(String::compareToIgnoreCase)) : comparator;
    }

    private Comparator<InventoryResponse> comparatorFor(Sort.Order order) {
        Comparator<InventoryResponse> comparator = switch (order.getProperty()) {
            case "productName" -> Comparator.comparing(InventoryResponse::getProductName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "sku" -> Comparator.comparing(InventoryResponse::getSku, Comparator.nullsLast(String::compareToIgnoreCase));
            case "quantity" -> Comparator.comparingInt(InventoryResponse::getQuantity);
            case "reservedQuantity" -> Comparator.comparingInt(InventoryResponse::getReservedQuantity);
            case "availableQuantity" -> Comparator.comparingInt(InventoryResponse::getAvailableQuantity);
            case "reorderLevel" -> Comparator.comparingInt(InventoryResponse::getReorderLevel);
            case "status" -> Comparator.comparing(InventoryResponse::getStatus, Comparator.nullsLast(String::compareToIgnoreCase));
            case "warehouseLocation" -> Comparator.comparing(InventoryResponse::getWarehouseLocation, Comparator.nullsLast(String::compareToIgnoreCase));
            default -> Comparator.comparing(InventoryResponse::getProductId, Comparator.nullsLast(String::compareToIgnoreCase));
        };
        return order.isAscending() ? comparator : comparator.reversed();
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
