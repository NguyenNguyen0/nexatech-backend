package com.nexatech.inventory.controller;

import com.nexatech.inventory.dto.response.ApiResponse;
import com.nexatech.inventory.dto.InventoryResponse;
import com.nexatech.inventory.dto.StockCheckRequest;
import com.nexatech.inventory.dto.StockUpdateRequest;
import com.nexatech.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

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
}
