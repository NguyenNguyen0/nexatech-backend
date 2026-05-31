package com.nexatech.product.client;

import com.nexatech.product.client.dto.InventoryStockResponse;
import com.nexatech.product.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @GetMapping("/api/v1/inventory/{productId}")
    InventoryStockResponse getStock(@PathVariable String productId);

    @GetMapping("/api/v1/inventory/batch")
    ApiResponse<List<InventoryStockResponse.StockData>> getStocks(@RequestParam("productIds") List<String> productIds);
}
