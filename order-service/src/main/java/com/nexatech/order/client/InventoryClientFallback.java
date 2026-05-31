package com.nexatech.order.client;

import com.nexatech.order.client.dto.StockOperationRequest;
import com.nexatech.order.dto.response.ApiResponse;
import org.springframework.stereotype.Component;

@Component
public class InventoryClientFallback implements InventoryClient {

    @Override
    public ApiResponse<Boolean> checkStock(StockOperationRequest request) {
        return ApiResponse.ok(false);
    }

    @Override
    public ApiResponse<Boolean> checkFinalizableStock(StockOperationRequest request) {
        return ApiResponse.ok(false);
    }

    @Override
    public ApiResponse<Void> reserveStock(StockOperationRequest request) {
        throw new IllegalStateException("Inventory service is unavailable; stock cannot be reserved");
    }

    @Override
    public ApiResponse<Void> releaseStock(StockOperationRequest request) {
        throw new IllegalStateException("Inventory service is unavailable; stock cannot be released");
    }
}
