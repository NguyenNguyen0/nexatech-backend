package com.nexatech.order.client;

import com.nexatech.order.client.dto.StockOperationRequest;
import com.nexatech.order.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client giao tiếp với inventory-service qua Eureka.
 * Sử dụng ApiResponse wrapper để khớp với chuẩn response của inventory-service.
 */
@FeignClient(name = "inventory-service", path = "/api/v1/inventory", fallback = InventoryClientFallback.class)
public interface InventoryClient {

    // Kiểm tra tồn kho — trả về true nếu đủ hàng, false nếu không
    @PostMapping("/check")
    ApiResponse<Boolean> checkStock(@RequestBody StockOperationRequest request);

    // Giữ hàng (giảm availableQuantity) khi đơn hàng được tạo
    @PostMapping("/reserve")
    ApiResponse<Void> reserveStock(@RequestBody StockOperationRequest request);

    // Giải phóng hàng đã giữ (tăng lại availableQuantity) khi đơn hàng bị huỷ
    @PostMapping("/release")
    ApiResponse<Void> releaseStock(@RequestBody StockOperationRequest request);
}
