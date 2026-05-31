package com.nexatech.inventory.service;

import com.nexatech.inventory.client.ProductClient;
import com.nexatech.inventory.client.dto.ProductSummary;
import com.nexatech.inventory.dto.BulkImportResult;
import com.nexatech.inventory.dto.CSVImportError;
import com.nexatech.inventory.dto.CSVImportResult;
import com.nexatech.inventory.dto.InventoryResponse;
import com.nexatech.inventory.dto.StockMovementResponse;
import com.nexatech.inventory.dto.StockCheckRequest;
import com.nexatech.inventory.dto.StockUpdateRequest;
import com.nexatech.inventory.dto.WarehouseUpdateRequest;
import com.nexatech.inventory.event.InventoryEvent;
import com.nexatech.inventory.event.InventoryEventProducer;
import com.nexatech.inventory.event.OrderEventDto;
import com.nexatech.inventory.exception.BadRequestException;
import com.nexatech.inventory.exception.ResourceNotFoundException;
import com.nexatech.inventory.model.InventoryItem;
import com.nexatech.inventory.model.StockMovement;
import com.nexatech.inventory.repository.InventoryItemRepository;
import com.nexatech.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.HashMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryService {

    private static final int DEFAULT_REORDER_LEVEL = 10;
    private static final int PRODUCT_LOOKUP_BATCH_SIZE = 50;
    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of(
            "productId", "productName", "sku", "quantity", "reservedQuantity", "availableQuantity",
            "reorderLevel", "status", "warehouseLocation");

    private final InventoryItemRepository inventoryItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryEventProducer inventoryEventProducer;
    private final ProductClient productClient;
    private final InventoryCacheInvalidationService cacheInvalidationService;

    @Value("${product.internal.api-key:change-me}")
    private String productInternalApiKey;

    public InventoryResponse getStock(String productId) {
        InventoryItem item = inventoryItemRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", "productId", productId));
        return toResponse(item, fetchProductSummary(item.getProductId()).orElse(null));
    }

    public List<InventoryResponse> getStocks(List<String> productIds) {
        List<String> normalizedIds = normalizeProductIds(productIds);
        if (normalizedIds.isEmpty()) {
            return List.of();
        }

        Map<String, InventoryItem> itemsByProductId = inventoryItemRepository.findByProductIdIn(normalizedIds).stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, Function.identity(), (left, right) -> left));

        return normalizedIds.stream()
                .map(itemsByProductId::get)
                .filter(Objects::nonNull)
                .map(item -> toResponse(item, null))
                .toList();
    }

        public Page<StockMovementResponse> getStockMovements(String productId, Pageable pageable) {
        inventoryItemRepository.findByProductId(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Inventory item", "productId", productId));

        Pageable safePageable = sanitizeMovementPageable(pageable);
        return stockMovementRepository.findByProductId(productId, safePageable)
            .map(this::toMovementResponse);
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

        if (request.getQuantity() < 0) {
            throw new BadRequestException("Quantity cannot be negative");
        }

        int oldQuantity = item.getQuantity();
        item.setQuantity(request.getQuantity());
        if (item.getReservedQuantity() > request.getQuantity()) {
            log.warn("Clamping reserved quantity because physical stock was lowered below reservations: productId={}, reserved={}, quantity={}",
                    item.getProductId(), item.getReservedQuantity(), request.getQuantity());
            item.setReservedQuantity(request.getQuantity());
        }
        InventoryItem saved = inventoryItemRepository.save(item);

        int delta = request.getQuantity() - oldQuantity;
        if (delta != 0) {
            recordMovement(saved.getProductId(), StockMovement.MovementType.ADJUSTMENT, Math.abs(delta),
                    "MANUAL_UPDATE", request.getReason(), null);
        }

        publishStockChangedEvent(saved, "INVENTORY_STOCK_UPDATED",
                StringUtils.hasText(request.getReason()) ? request.getReason() : "Manual stock update");
        publishLowStockEventIfNeeded(saved);
        return toResponse(saved, productSummary);
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

    public boolean canFinalizeSale(StockCheckRequest request) {
        validateItems(request);

        for (StockCheckRequest.Item itemRequest : request.getItems()) {
            InventoryItem item = inventoryItemRepository.findByProductId(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory item", "productId", itemRequest.getProductId()));

            if (item.getQuantity() < itemRequest.getQuantity()) {
                return false;
            }
        }
        return true;
    }

    @Transactional
    public void reserveStock(StockCheckRequest request) {
        reserveStock(
                request,
                resolveReference(request, "API_RESERVE"),
                resolveReason(request, "Stock reserved from API")
        );
    }

    @Transactional
    public void releaseStock(StockCheckRequest request) {
        releaseStock(
                request,
                resolveReference(request, "API_RELEASE"),
                resolveReason(request, "Stock released from API")
        );
    }

    public List<InventoryResponse> getLowStockItems() {
        List<InventoryItem> lowStockItems = inventoryItemRepository.findAll().stream()
                .filter(this::isLowStock)
                .toList();
        Map<String, ProductSummary> productSummaries = fetchProductSummaries(
                lowStockItems.stream().map(InventoryItem::getProductId).toList());

        return lowStockItems.stream()
                .map(item -> toResponse(item, productSummaries.get(item.getProductId())))
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
        List<InventoryItem> candidates = inventoryItemRepository.findAll().stream()
                .filter(item -> matchesWarehouse(item, warehouseLocation))
                .filter(item -> matchesSku(item, sku))
                .toList();
        Map<String, ProductSummary> productSummaries = fetchProductSummaries(
                candidates.stream().map(InventoryItem::getProductId).toList());

        List<InventoryResponse> filtered = candidates.stream()
                .map(item -> toResponse(item, productSummaries.get(item.getProductId())))
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
        publishStockChangedEvent(saved, "INVENTORY_STOCK_UPDATED", "Warehouse information updated");
        publishLowStockEventIfNeeded(saved);
        return toResponse(saved, fetchProductSummary(saved.getProductId()).orElse(null));
    }

    @Transactional
    public void reserveStock(StockCheckRequest request, String reference, String reason) {
        validateItems(request);
        if (!checkStock(request)) {
            throw new BadRequestException("Insufficient stock for one or more products");
        }

        for (StockCheckRequest.Item itemRequest : request.getItems()) {
            if (hasMovement(itemRequest.getProductId(), StockMovement.MovementType.RESERVED, reference)) {
                log.info("Skip duplicate stock reservation: productId={} reference={}", itemRequest.getProductId(), reference);
                continue;
            }

            InventoryItem item = inventoryItemRepository.findByProductId(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory item", "productId", itemRequest.getProductId()));

            item.setReservedQuantity(item.getReservedQuantity() + itemRequest.getQuantity());
            InventoryItem saved = inventoryItemRepository.save(item);

            recordMovement(saved.getProductId(), StockMovement.MovementType.RESERVED,
                    itemRequest.getQuantity(), reference, reason, null);
            publishStockChangedEvent(saved, "INVENTORY_STOCK_RESERVED", reason);
            publishLowStockEventIfNeeded(saved);
        }
    }

    @Transactional
    public void releaseStock(StockCheckRequest request, String reference, String reason) {
        validateItems(request);

        for (StockCheckRequest.Item itemRequest : request.getItems()) {
            if (hasMovement(itemRequest.getProductId(), StockMovement.MovementType.RELEASED, reference)) {
                log.info("Skip duplicate stock release: productId={} reference={}", itemRequest.getProductId(), reference);
                continue;
            }

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
            publishStockChangedEvent(item, "INVENTORY_STOCK_RELEASED", reason);
        }
    }

    @Transactional
    public void releaseOrRestock(StockCheckRequest request, String reference, String reason) {
        validateItems(request);

        for (StockCheckRequest.Item itemRequest : request.getItems()) {
            InventoryItem item = inventoryItemRepository.findByProductId(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory item", "productId", itemRequest.getProductId()));

            boolean wasSold = hasMovement(itemRequest.getProductId(), StockMovement.MovementType.SALE, reference);

            if (wasSold) {
                if (hasMovement(itemRequest.getProductId(), StockMovement.MovementType.RETURN, reference)) {
                    log.info("Skip duplicate restock for reference={}: productId={}", reference, itemRequest.getProductId());
                    continue;
                }
                item.setQuantity(item.getQuantity() + itemRequest.getQuantity());
                InventoryItem saved = inventoryItemRepository.save(item);
                recordMovement(item.getProductId(), StockMovement.MovementType.RETURN,
                        itemRequest.getQuantity(), reference, reason + " (Restocked from finalized sale)", null);
                publishStockChangedEvent(saved, "INVENTORY_STOCK_RESTOCKED", reason);
            } else {
                if (hasMovement(itemRequest.getProductId(), StockMovement.MovementType.RELEASED, reference)) {
                    log.info("Skip duplicate release for reference={}: productId={}", reference, itemRequest.getProductId());
                    continue;
                }
                if (item.getReservedQuantity() >= itemRequest.getQuantity()) {
                    item.setReservedQuantity(item.getReservedQuantity() - itemRequest.getQuantity());
                    InventoryItem saved = inventoryItemRepository.save(item);
                    publishStockChangedEvent(saved, "INVENTORY_STOCK_RELEASED", reason);
                } else {
                    log.warn("Reserved quantity underflow for reference={}, productId={}, reserved={}, release={}",
                            reference, itemRequest.getProductId(), item.getReservedQuantity(), itemRequest.getQuantity());
                    item.setReservedQuantity(0);
                    InventoryItem saved = inventoryItemRepository.save(item);
                    publishStockChangedEvent(saved, "INVENTORY_STOCK_RELEASED", reason);
                }
                recordMovement(item.getProductId(), StockMovement.MovementType.RELEASED,
                        itemRequest.getQuantity(), reference, reason, null);
            }
        }
    }

    @Transactional
    public void finalizeSale(StockCheckRequest request, String reference) {
        validateItems(request);

        for (StockCheckRequest.Item itemRequest : request.getItems()) {
            if (hasMovement(itemRequest.getProductId(), StockMovement.MovementType.SALE, reference)) {
                log.info("Skip duplicate sale finalization: productId={} reference={}", itemRequest.getProductId(), reference);
                continue;
            }

            InventoryItem item = inventoryItemRepository.findByProductId(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory item", "productId", itemRequest.getProductId()));

            if (item.getQuantity() < itemRequest.getQuantity()) {
                throw new BadRequestException("Insufficient physical stock for product: " + itemRequest.getProductId());
            }
            if (item.getQuantity() < itemRequest.getQuantity()) {
                throw new BadRequestException("Insufficient physical stock for product: " + itemRequest.getProductId());
            }

            // Use Math.min to guard against underflow if admin forced quantity below reserved
            int reservedToRelease = Math.min(item.getReservedQuantity(), itemRequest.getQuantity());
            item.setReservedQuantity(item.getReservedQuantity() - reservedToRelease);
            item.setQuantity(item.getQuantity() - itemRequest.getQuantity());
            InventoryItem saved = inventoryItemRepository.save(item);

            recordMovement(saved.getProductId(), StockMovement.MovementType.SALE,
                    itemRequest.getQuantity(), reference, "Finalized sale from order event", null);
            publishStockChangedEvent(saved, "INVENTORY_STOCK_SOLD", "Finalized sale from order event");
            publishLowStockEventIfNeeded(saved);
        }
    }

    /**
     * Hoàn kho khi đơn hàng trả hàng được duyệt (ORDER_RETURN_APPROVED).
     * Chỉ cộng lại quantity (không đụng reservedQuantity vì finalizeSale đã giảm cả hai khi giao hàng).
     */
    @Transactional
    public void restockReturn(StockCheckRequest request, String reference) {
        validateItems(request);

        for (StockCheckRequest.Item itemRequest : request.getItems()) {
            if (hasMovement(itemRequest.getProductId(), StockMovement.MovementType.RETURN, reference)) {
                log.info("Skip duplicate return restock: productId={} reference={}", itemRequest.getProductId(), reference);
                continue;
            }

            InventoryItem item = inventoryItemRepository.findByProductId(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory item", "productId", itemRequest.getProductId()));

            item.setQuantity(item.getQuantity() + itemRequest.getQuantity());
            InventoryItem saved = inventoryItemRepository.save(item);

            recordMovement(saved.getProductId(), StockMovement.MovementType.RETURN,
                    itemRequest.getQuantity(), reference, "Stock restocked from approved return", null);
            publishStockChangedEvent(saved, "INVENTORY_STOCK_RESTOCKED", "Stock restocked from approved return");
            publishLowStockEventIfNeeded(saved);
        }
    }

    public void publishOrderOutOfStockEvent(OrderEventDto orderEvent, String reason) {
        String productId = orderEvent.getItems() != null && !orderEvent.getItems().isEmpty()
                ? orderEvent.getItems().get(0).getProductId()
                : null;
        InventoryEvent event = InventoryEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("INVENTORY_OUT_OF_STOCK")
                .timestamp(Instant.now())
                .orderId(orderEvent.getOrderId())
                .orderNumber(orderEvent.getOrderNumber())
                .productId(productId)
                .reason(reason)
                .message("Inventory could not finalize the order because stock is no longer available")
                .build();
        inventoryEventProducer.publishInventoryEvent(
                orderEvent.getOrderId() != null ? orderEvent.getOrderId().toString() : productId,
                event);
    }

    public void publishOrderStockFinalizedEvent(OrderEventDto orderEvent) {
        String productId = orderEvent.getItems() != null && !orderEvent.getItems().isEmpty()
                ? orderEvent.getItems().get(0).getProductId()
                : null;
        InventoryEvent event = InventoryEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("INVENTORY_SALE_FINALIZED")
                .timestamp(Instant.now())
                .orderId(orderEvent.getOrderId())
                .orderNumber(orderEvent.getOrderNumber())
                .productId(productId)
                .reason("Inventory finalized for paid order")
                .message("Inventory sale was finalized successfully")
                .build();
        inventoryEventProducer.publishInventoryEvent(
                orderEvent.getOrderId() != null ? orderEvent.getOrderId().toString() : productId,
                event);
    }

    private void validateItems(StockCheckRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Items list must not be empty");
        }
        for (StockCheckRequest.Item item : request.getItems()) {
            if (item == null || !StringUtils.hasText(item.getProductId())) {
                throw new BadRequestException("ProductId is required for every stock item");
            }
            if (item.getQuantity() <= 0) {
                throw new BadRequestException("Quantity must be greater than zero for product: " + item.getProductId());
            }
        }
    }

    private InventoryResponse toResponse(InventoryItem item, ProductSummary productSummary) {
        int availableQuantity = Math.max(0, item.getQuantity() - item.getReservedQuantity());
        InventoryResponse.InventoryResponseBuilder builder = InventoryResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(productSummary != null ? productSummary.getName() : null)
                .productSlug(productSummary != null ? productSummary.getSlug() : null)
                .productThumbnail(productSummary != null ? productSummary.getThumbnail() : null)
                .sku(item.getSku())
                .quantity(item.getQuantity())
                .reservedQuantity(item.getReservedQuantity())
                .availableQuantity(availableQuantity)
                .reorderLevel(item.getReorderLevel())
                .warehouseLocation(item.getWarehouseLocation())
                .status(resolveStatus(availableQuantity, item.getReorderLevel()));

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

    private boolean hasMovement(String productId, StockMovement.MovementType type, String reference) {
        return reference != null
                && stockMovementRepository.existsByProductIdAndTypeAndReference(productId, type, reference);
    }

    private String resolveReference(StockCheckRequest request, String fallback) {
        return request != null && StringUtils.hasText(request.getReference())
                ? request.getReference().trim()
                : fallback;
    }

    private String resolveReason(StockCheckRequest request, String fallback) {
        return request != null && StringUtils.hasText(request.getReason())
                ? request.getReason().trim()
                : fallback;
    }

    private StockMovementResponse toMovementResponse(StockMovement movement) {
        return StockMovementResponse.builder()
                .id(movement.getId())
                .productId(movement.getProductId())
                .type(movement.getType())
                .quantity(movement.getQuantity())
                .reference(movement.getReference())
                .reason(movement.getReason())
                .performedBy(movement.getPerformedBy())
                .createdAt(movement.getCreatedAt())
                .build();
    }

    private boolean isLowStock(InventoryItem item) {
        return item.getQuantity() - item.getReservedQuantity() <= item.getReorderLevel();
    }

    private void publishStockChangedEvent(InventoryItem item, String eventType, String reason) {
        InventoryEvent event = InventoryEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .timestamp(Instant.now())
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .reorderLevel(item.getReorderLevel())
                .warehouseLocation(item.getWarehouseLocation())
                .reason(reason)
                .message("Inventory stock changed")
                .build();
        inventoryEventProducer.publishInventoryEvent(item.getProductId(), event);
        cacheInvalidationService.evictForProduct(item.getProductId(), eventType);
    }

    private Optional<ProductSummary> fetchProductSummary(String productId) {
        return Optional.ofNullable(fetchProductSummaries(List.of(productId)).get(productId));
    }

    private Map<String, ProductSummary> fetchProductSummaries(Collection<String> productIds) {
        List<String> normalizedIds = normalizeProductIds(productIds);
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        Map<String, ProductSummary> summaries = new HashMap<>();
        for (int start = 0; start < normalizedIds.size(); start += PRODUCT_LOOKUP_BATCH_SIZE) {
            int end = Math.min(start + PRODUCT_LOOKUP_BATCH_SIZE, normalizedIds.size());
            List<String> batch = normalizedIds.subList(start, end);
            try {
                Optional.ofNullable(productClient.lookupProducts(productInternalApiKey, batch))
                        .map(response -> response.getData())
                        .orElse(List.of())
                        .forEach(summary -> {
                            if (summary != null && StringUtils.hasText(summary.getId())) {
                                summaries.put(summary.getId(), summary);
                            }
                        });
            } catch (Exception e) {
                log.warn("Could not fetch product summaries for {} inventory items: {}", batch.size(), e.getMessage());
            }
        }
        return summaries;
    }

    private List<String> normalizeProductIds(Collection<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return productIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
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

    private Pageable sanitizeMovementPageable(Pageable pageable) {
        int page = pageable == null ? 0 : pageable.getPageNumber();
        int size = pageable == null ? 20 : pageable.getPageSize();

        Sort safeSort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (pageable != null && pageable.getSort().isSorted()) {
            List<Sort.Order> accepted = pageable.getSort().stream()
                    .filter(order -> "createdAt".equals(order.getProperty())
                            || "type".equals(order.getProperty())
                            || "quantity".equals(order.getProperty()))
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

                    publishStockChangedEvent(saved, "INVENTORY_STOCK_UPDATED", "Imported from CSV line " + lineNumber);
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
