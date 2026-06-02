package com.nexatech.inventory.controller;

import com.nexatech.inventory.constant.PermissionsAccept;
import com.nexatech.inventory.dto.response.ApiResponse;
import com.nexatech.inventory.dto.BulkImportResult;
import com.nexatech.inventory.dto.CSVImportResult;
import com.nexatech.inventory.dto.InventoryResponse;
import com.nexatech.inventory.dto.PageResponse;
import com.nexatech.inventory.dto.StockMovementResponse;
import com.nexatech.inventory.dto.StockCheckRequest;
import com.nexatech.inventory.dto.StockUpdateRequest;
import com.nexatech.inventory.dto.WarehouseUpdateRequest;
import com.nexatech.inventory.security.RequiresPermission;
import com.nexatech.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/list")
    // @RequiresPermission(PermissionsAccept.Inventory.READ)
    @Cacheable(
            value = "inventoryLists",
            key = "T(com.nexatech.inventory.util.InventoryCacheKey).of('list', #sku, #status, #keyword, #warehouseLocation, #pageable)",
            unless = "#result == null || #result.data == null")
    public ApiResponse<PageResponse<InventoryResponse>> getAllInventory(
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String warehouseLocation,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
                inventoryService.getAllInventory(sku, status, keyword, warehouseLocation, pageable)));
    }

    @GetMapping("/batch")
    @Cacheable(
            value = "inventoryStockBatch",
            key = "T(com.nexatech.inventory.util.InventoryCacheKey).of('batch', #productIds)",
            unless = "#result == null || #result.data == null")
    public ApiResponse<List<InventoryResponse>> getStocks(@RequestParam List<String> productIds) {
        return ApiResponse.ok(inventoryService.getStocks(productIds));
    }

    @GetMapping("/{productId}")
    @Cacheable(value = "inventoryStock", key = "#productId", unless = "#result == null || #result.data == null")
    public ApiResponse<InventoryResponse> getStock(@PathVariable String productId) {
        return ApiResponse.ok(inventoryService.getStock(productId));
    }

    @GetMapping("/{productId}/movements")
    // @RequiresPermission(PermissionsAccept.Inventory.READ)
    @Cacheable(
            value = "inventoryMovements",
            key = "T(com.nexatech.inventory.util.InventoryCacheKey).of(#productId, #pageable)",
            unless = "#result == null || #result.data == null")
    public ApiResponse<PageResponse<StockMovementResponse>> getStockMovements(
            @PathVariable String productId,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(inventoryService.getStockMovements(productId, pageable)));
    }

    @PutMapping
    // @RequiresPermission(PermissionsAccept.Inventory.WRITE)
    @CachePut(value = "inventoryStock", key = "#request.productId", unless = "#result == null || #result.data == null")
    public ApiResponse<InventoryResponse> updateStock(@Valid @RequestBody StockUpdateRequest request) {
        return ApiResponse.ok(inventoryService.updateStock(request));
    }

    @PostMapping("/check")
    public ApiResponse<Boolean> checkStock(@Valid @RequestBody StockCheckRequest request) {
        return ApiResponse.ok(inventoryService.checkStock(request));
    }

    @PostMapping("/finalize/check")
    public ApiResponse<Boolean> checkFinalizableStock(@Valid @RequestBody StockCheckRequest request) {
        return ApiResponse.ok(inventoryService.canFinalizeSale(request));
    }

    @PostMapping("/reserve")
    public ApiResponse<Void> reserveStock(@Valid @RequestBody StockCheckRequest request) {
        inventoryService.reserveStock(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/release")
    public ApiResponse<Void> releaseStock(@Valid @RequestBody StockCheckRequest request) {
        inventoryService.releaseStock(request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/low-stock")
    // @RequiresPermission(PermissionsAccept.Inventory.READ)
    @Cacheable(value = "lowStockInventory", key = "'all'", unless = "#result == null || #result.data == null")
    public ApiResponse<List<InventoryResponse>> getLowStockItems() {
        return ApiResponse.ok(inventoryService.getLowStockItems());
    }

    @PostMapping("/bulk-import")
    // @RequiresPermission(PermissionsAccept.Inventory.IMPORT)
    public ApiResponse<BulkImportResult> bulkImport(@Valid @RequestBody List<StockUpdateRequest> requests) {
        return ApiResponse.ok(inventoryService.bulkImport(requests));
    }

    @PostMapping("/import")
    // @RequiresPermission(PermissionsAccept.Inventory.IMPORT)
    public ApiResponse<CSVImportResult> importFromCSV(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(inventoryService.importFromCSV(file));
    }

    @PatchMapping("/{productId}/warehouse")
    // @RequiresPermission(PermissionsAccept.Inventory.WRITE)
    @CachePut(value = "inventoryStock", key = "#productId", unless = "#result == null || #result.data == null")
    public ApiResponse<InventoryResponse> updateWarehouseInfo(
            @PathVariable String productId,
            @Valid @RequestBody WarehouseUpdateRequest request) {
        return ApiResponse.ok(inventoryService.updateWarehouseInfo(productId, request));
    }
}
