package com.nexatech.order.client;

import com.nexatech.order.client.dto.ProductInfo;
import com.nexatech.order.client.dto.StockOperationRequest;
import com.nexatech.order.dto.response.ApiResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class InventoryClientFallback implements InventoryClient {

    @Override
    public ApiResponse<Boolean> checkStock(StockOperationRequest request) {
        return ApiResponse.ok(true);
    }

    @Override
    public ApiResponse<Void> reserveStock(StockOperationRequest request) {
        return ApiResponse.ok(null);
    }

    @Override
    public ApiResponse<Void> releaseStock(StockOperationRequest request) {
        return ApiResponse.ok(null);
    }
}
