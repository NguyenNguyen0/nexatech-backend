package com.nexatech.inventory.controller;

import com.nexatech.inventory.dto.response.ApiResponse;
import com.nexatech.inventory.dto.BulkImportResult;
import com.nexatech.inventory.dto.CSVImportResult;
import com.nexatech.inventory.dto.InventoryResponse;
import com.nexatech.inventory.dto.StockCheckRequest;
import com.nexatech.inventory.dto.StockUpdateRequest;
import com.nexatech.inventory.dto.WarehouseUpdateRequest;
import com.nexatech.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
    public ApiResponse<Page<InventoryResponse>> getAllInventory(
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String warehouseLocation,
            Pageable pageable) {
        return ApiResponse.ok(inventoryService.getAllInventory(sku, status, keyword, warehouseLocation, pageable));
    }

    @GetMapping("/{productId}")
    public ApiResponse<InventoryResponse> getStock(@PathVariable String productId) {
        return ApiResponse.ok(inventoryService.getStock(productId));
    }

    @PutMapping
    public ApiResponse<InventoryResponse> updateStock(@Valid @RequestBody StockUpdateRequest request) {
        return ApiResponse.ok(inventoryService.updateStock(request));
    }

    @PostMapping("/check")
    public ApiResponse<Boolean> checkStock(@Valid @RequestBody StockCheckRequest request) {
        return ApiResponse.ok(inventoryService.checkStock(request));
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
    public ApiResponse<List<InventoryResponse>> getLowStockItems() {
        return ApiResponse.ok(inventoryService.getLowStockItems());
    }

    @PostMapping("/bulk-import")
    public ApiResponse<BulkImportResult> bulkImport(@Valid @RequestBody List<StockUpdateRequest> requests) {
        return ApiResponse.ok(inventoryService.bulkImport(requests));
    }

    @PostMapping("/import")
    public ApiResponse<CSVImportResult> importFromCSV(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(inventoryService.importFromCSV(file));
    }

    @PatchMapping("/{productId}/warehouse")
    public ApiResponse<InventoryResponse> updateWarehouseInfo(
            @PathVariable String productId,
            @Valid @RequestBody WarehouseUpdateRequest request) {
        return ApiResponse.ok(inventoryService.updateWarehouseInfo(productId, request));
    }
}
